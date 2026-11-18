package com.example.demo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.recruiter.RecruiterDtos.*;
import com.example.demo.dto.DashboardStatsDtos.RecruiterDashboardStats;
import com.example.demo.entity.*;
import com.example.demo.entity.Application.Status;
import com.example.demo.repository.*;

@Service
public class RecruiterService {
    private static final int MAX_STATUS_NOTE_LENGTH = 1000;

    private final UserRepository users;
    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final StudentProfileRepository profiles;
    private final ApplicationStatusHistoryRepository history;
    private final InterviewScheduleRepository interviews;
    private final NotificationRepository notifications;
    private final OfferRepository offers;
    private final AuditLogRepository auditLogs;
    private final Path resumeDirectory;

    public RecruiterService(UserRepository users, CompanyRepository companies, JobRepository jobs,
            ApplicationRepository applications, StudentProfileRepository profiles,
            ApplicationStatusHistoryRepository history, InterviewScheduleRepository interviews,
            NotificationRepository notifications, OfferRepository offers, AuditLogRepository auditLogs,
            @Value("${app.storage.resumes}") String resumeDirectory) {
        this.users = users; this.companies = companies; this.jobs = jobs; this.applications = applications;
        this.profiles = profiles; this.history = history; this.interviews = interviews; this.notifications = notifications;
        this.resumeDirectory = Path.of(resumeDirectory).toAbsolutePath().normalize();
        this.offers = offers;
        this.auditLogs = auditLogs;
    }

    public CompanyResponse company(String email) {
        User recruiter = recruiter(email);
        Company company = companies.findByOwnerUserId(recruiter.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company profile not found"));
        return toCompany(company);
    }

    public CompanyResponse saveCompany(String email, CompanyRequest request) {
        User recruiter = recruiter(email);
        Company company = companies.findByOwnerUserId(recruiter.getId()).orElse(
                Company.builder().ownerUserId(recruiter.getId()).verified(false).build());
        company.setName(request.name().trim());
        company.setIndustry(request.industry()); company.setWebsite(request.website());
        company.setLocation(request.location()); company.setDescription(request.description());
        company.setLogoUrl(request.logoUrl());
        return toCompany(companies.save(company));
    }

    public Job createJob(String email, JobRequest request) {
        User recruiter = recruiter(email);
        Company company = companies.findByOwnerUserId(recruiter.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "Create a company profile first"));
        if (!company.isVerified())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company must be verified before creating jobs");
        Job job = Job.builder().title(request.title()).description(request.description())
                .companyName(request.companyName()).minCgpa(request.minCgpa() == null ? 0.0 : request.minCgpa())
                .maxBacklogs(request.maxBacklogs() == null ? 0 : request.maxBacklogs()).deadline(request.deadline())
                .recruiterId(recruiter.getId()).location(request.location()).jobType(request.jobType())
                .requiredSkills(request.requiredSkills()).salaryMin(request.salaryMin()).salaryMax(request.salaryMax())
                .internship(request.internship()).build();
        return jobs.save(job);
    }

    public Job publishJob(String email, Long jobId) {
        Job job = ownedJob(email, jobId);
        Company company = companies.findByOwnerUserId(recruiter(email).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Company profile required"));
        if (!company.isVerified())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company must be verified before publishing jobs");
        if (job.getDeadline().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expired jobs cannot be published");
        job.setPublished(true);
        job.setClosed(false);
        return jobs.save(job);
    }

    public Job closeJob(String email, Long jobId) {
        Job job = ownedJob(email, jobId);
        job.setClosed(true);
        job.setPublished(false);
        return jobs.save(job);
    }

    public Job updateJob(String email, Long jobId, JobRequest request) {
        Job job = ownedJob(email, jobId);
        job.setTitle(request.title()); job.setDescription(request.description());
        job.setCompanyName(request.companyName()); job.setMinCgpa(request.minCgpa());
        job.setMaxBacklogs(request.maxBacklogs()); job.setDeadline(request.deadline());
        job.setLocation(request.location()); job.setJobType(request.jobType()); job.setRequiredSkills(request.requiredSkills());
        job.setSalaryMin(request.salaryMin()); job.setSalaryMax(request.salaryMax()); job.setInternship(request.internship());
        return jobs.save(job);
    }

    public List<Job> jobs(String email) {
        return jobs.findByRecruiterId(recruiter(email).getId());
    }

    @Transactional(readOnly = true)
    public RecruiterDashboardStats dashboardStats(String email) {
        User recruiter = recruiter(email);
        Long recruiterId = recruiter.getId();
        List<Object[]> offerCounts = offers.countByStatusForRecruiter(recruiterId);
        return new RecruiterDashboardStats(jobs.countNonInternshipJobsByRecruiter(recruiterId),
                jobs.countByRecruiterIdAndInternshipTrue(recruiterId),
                jobs.countActiveJobsByRecruiter(recruiterId, LocalDate.now()),
                applications.countDistinctApplicantsByRecruiter(recruiterId),
                DashboardStatsMapper.completeCounts(Status.class,
                        applications.countByStatusForRecruiter(recruiterId)),
                applications.countDistinctApplicantsByRecruiterAndStatus(recruiterId, Status.SHORTLISTED),
                applications.countDistinctApplicantsByRecruiterAndStatus(recruiterId, Status.REJECTED),
                interviews.countUpcomingByRecruiter(recruiterId,
                        List.of(InterviewSchedule.Status.SCHEDULED, InterviewSchedule.Status.RESCHEDULED),
                        Instant.now()),
                offerCounts.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum(),
                countOfferStatus(offerCounts, Offer.Status.ACCEPTED),
                countOfferStatus(offerCounts, Offer.Status.DECLINED));
    }

    public void deleteJob(String email, Long jobId) {
        Job job = ownedJob(email, jobId);
        if (!applications.findByJobId(jobId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Jobs with applications cannot be deleted");
        jobs.delete(job);
    }

    @Transactional(readOnly = true)
    public List<ApplicantResponse> applicants(String email, Long jobId) {
        Job job = ownedJob(email, jobId);
        return applications.findByJobId(job.getId()).stream().map(this::toApplicant).toList();
    }

    @Transactional
    public ApplicantResponse updateStatus(String email, Long applicationId, StatusRequest request) {
        Application application = ownedApplication(email, applicationId);
        Status next;
        try { next = Status.valueOf(request.status().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status"); }
        validateTransition(application.getStatus(), next);
        if ((request.note() != null && request.note().length() > MAX_STATUS_NOTE_LENGTH)
                || (request.rejectionExplanation() != null
                        && request.rejectionExplanation().length() > MAX_STATUS_NOTE_LENGTH))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application status notes must not exceed 1000 characters");
        String note;
        if (next == Status.REJECTED) {
            note = trimToNull(request.rejectionExplanation());
            if (note == null) note = trimToNull(request.note());
            if (note == null || note.length() < 10)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A rejection explanation of at least 10 characters is required");
        } else {
            note = trimToNull(request.note());
        }
        application.setStatus(next);
        applications.save(application);
        history.save(ApplicationStatusHistory.builder().application(application).status(next)
                .changedBy(email).changedAt(Instant.now()).note(note).build());
        notifications.save(Notification.builder().user(application.getStudent()).title("Application status updated")
                .message(application.getJob().getTitle() + ": " + next).type("APPLICATION_STATUS")
                .readFlag(false).createdAt(Instant.now()).build());
        return toApplicant(application);
    }

    @Transactional
    public InterviewDetailResponse scheduleInterview(String email, Long applicationId, InterviewRequest request) {
        Application application = ownedApplication(email, applicationId);
        if (!Set.of(Status.SHORTLISTED, Status.INTERVIEW_SCHEDULED).contains(application.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only shortlisted applications can be interviewed");
        if (request.scheduledAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview time must be in the future");
        application.setStatus(Status.INTERVIEW_SCHEDULED);
        applications.save(application);
        history.save(ApplicationStatusHistory.builder().application(application)
                .status(Status.INTERVIEW_SCHEDULED).changedBy(email).changedAt(Instant.now())
                .note("Interview scheduled").build());
        InterviewSchedule interview = InterviewSchedule.builder().application(application)
                .scheduledAt(request.scheduledAt()).mode(request.mode()).locationOrLink(request.locationOrLink())
                .notes(request.notes()).createdAt(Instant.now()).build();
        InterviewSchedule saved = interviews.save(interview);
        notifications.save(Notification.builder().user(application.getStudent()).title("Interview scheduled")
                .message(application.getJob().getTitle() + " interview scheduled for " + request.scheduledAt())
                .type("INTERVIEW").readFlag(false).createdAt(Instant.now()).build());
        return toInterview(saved);
    }

    @Transactional
    public InterviewDetailResponse submitFeedback(String email, Long applicationId, Long interviewId, FeedbackRequest request) {
        Application application = ownedApplication(email, applicationId);
        InterviewSchedule interview = interviews.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!interview.getApplication().getId().equals(applicationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview does not belong to application");
        }
        if (interview.getStatus() == InterviewSchedule.Status.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled interviews cannot receive feedback");
        if (interview.getStatus() == InterviewSchedule.Status.COMPLETED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed interview feedback cannot be changed");
        if (interview.getScheduledAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feedback can only be submitted after the interview");
        interview.setFeedback(request.feedback());
        interview.setStatus(InterviewSchedule.Status.COMPLETED);
        return toInterview(interviews.save(interview));
    }

    @Transactional
    public InterviewDetailResponse rescheduleInterview(String email, Long applicationId, Long interviewId, InterviewRequest request) {
        Application application = ownedApplication(email, applicationId);
        InterviewSchedule interview = ownedInterview(application, interviewId);
        if (Set.of(InterviewSchedule.Status.COMPLETED, InterviewSchedule.Status.CANCELLED).contains(interview.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview cannot be rescheduled");
        if (request.scheduledAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview time must be in the future");
        boolean scheduleChanged = !Objects.equals(interview.getScheduledAt(), request.scheduledAt())
                || !Objects.equals(interview.getMode(), request.mode())
                || !Objects.equals(normalizeOptionalForComparison(interview.getLocationOrLink()),
                        normalizeOptionalForComparison(request.locationOrLink()))
                || !Objects.equals(normalizeOptionalForComparison(interview.getNotes()),
                        normalizeOptionalForComparison(request.notes()));
        interview.setScheduledAt(request.scheduledAt()); interview.setMode(request.mode());
        interview.setLocationOrLink(request.locationOrLink()); interview.setNotes(request.notes());
        interview.setStatus(InterviewSchedule.Status.RESCHEDULED);
        InterviewSchedule saved = interviews.save(interview);
        if (scheduleChanged)
            notifyStudent(application, "Interview rescheduled",
                    application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                            + ": interview rescheduled to " + saved.getScheduledAt() + ".");
        return toInterview(saved);
    }

    @Transactional
    public InterviewDetailResponse cancelInterview(String email, Long applicationId, Long interviewId) {
        Application application = ownedApplication(email, applicationId);
        InterviewSchedule interview = ownedInterview(application, interviewId);
        if (Set.of(InterviewSchedule.Status.COMPLETED, InterviewSchedule.Status.CANCELLED).contains(interview.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview cannot be cancelled");
        interview.setStatus(InterviewSchedule.Status.CANCELLED);
        interview.setCancelledAt(Instant.now());
        InterviewSchedule saved = interviews.save(interview);
        notifyStudent(application, "Interview cancelled",
                application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                        + ": interview scheduled for " + saved.getScheduledAt() + " was cancelled.");
        return toInterview(saved);
    }

    @Transactional(readOnly = true)
    public List<InterviewDetailResponse> interviews(String email) {
        recruiter(email);
        return jobs.findByRecruiterId(recruiter(email).getId()).stream()
                .flatMap(job -> interviews.findByApplicationJobId(job.getId()).stream())
                .map(this::toInterview).toList();
    }

    @Transactional(readOnly = true)
    public byte[] downloadResume(String email, Long applicationId) throws Exception {
        Application application = ownedApplication(email, applicationId);
        StudentProfile profile = profiles.findByUserId(application.getStudent().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student profile not found"));
        if (profile.getResumeUrl() == null || profile.getResumeUrl().isBlank())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        Path resume = resumeDirectory.resolve(profile.getResumeUrl()).normalize();
        if (!resume.startsWith(resumeDirectory)
                || !Files.isRegularFile(resume, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        return Files.readAllBytes(resume);
    }

    @Transactional
    public OfferResponse createOffer(String email, Long applicationId, OfferRequest request) {
        Application application = ownedApplication(email, applicationId);
        application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (application.getStatus() != Status.OFFERED && application.getStatus() != Status.INTERVIEW_SCHEDULED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A valid application is required");
        if (offers.findByApplicationId(applicationId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer already exists for this application");
        if (application.getStatus() != Status.OFFERED) {
            application.setStatus(Status.OFFERED);
            applications.save(application);
            history.save(ApplicationStatusHistory.builder().application(application).status(Status.OFFERED)
                    .changedBy(email).changedAt(Instant.now()).note("Offer issued").build());
        }
        Offer offer = Offer.builder().application(application).packageDetails(request.packageDetails())
                .packageAmount(request.packageAmount()).joiningDate(request.joiningDate())
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Offer saved = offers.save(offer);
        notifyStudent(application, "Offer received",
                application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                        + ": a job offer is available in your offers.");
        audit(email, "OFFER_CREATED", applicationId, saved.getId());
        return toOffer(saved);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> recruiterOffers(String email) {
        User recruiter = recruiter(email);
        return jobs.findByRecruiterId(recruiter.getId()).stream()
                .flatMap(job -> offers.findByApplicationJobId(job.getId()).stream()).map(this::toOffer).toList();
    }

    @Transactional
    public OfferResponse updateOfferStatus(String email, Long offerId, OfferStatusRequest request) {
        Offer offer = offers.findByIdForUpdate(offerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        Application application = ownedApplication(email, offer.getApplication().getId());
        Offer.Status next;
        try { next = Offer.Status.valueOf(request.status().trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offer status"); }
        if (offer.getStatus() != Offer.Status.ISSUED || next != Offer.Status.WITHDRAWN)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid offer status transition");
        offer.setStatus(next); offer.setUpdatedAt(Instant.now()); offers.save(offer);
        notifyStudent(application, "Offer withdrawn",
                application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                        + ": the job offer has been withdrawn.");
        audit(email, "OFFER_" + next.name(), application.getId(), offer.getId());
        return toOffer(offer);
    }

    private User recruiter(String email) {
        User user = users.findByEmail(email.toLowerCase()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != User.Role.RECRUITER)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recruiter access required");
        return user;
    }
    private Job ownedJob(String email, Long jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.getRecruiterId().equals(recruiter(email).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Job belongs to another recruiter");
        return job;
    }
    private Application ownedApplication(String email, Long applicationId) {
        Application application = applications.findById(applicationId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        ownedJob(email, application.getJob().getId());
        return application;
    }
    private InterviewSchedule ownedInterview(Application application, Long interviewId) {
        InterviewSchedule interview = interviews.findByIdForUpdate(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!interview.getApplication().getId().equals(application.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview does not belong to application");
        return interview;
    }
    private OfferResponse toOffer(Offer offer) {
        return new OfferResponse(offer.getId(), offer.getApplication().getId(), offer.getPackageDetails(),
                offer.getPackageAmount(), offer.getJoiningDate(), offer.getStatus().name(),
                offer.getCreatedAt(), offer.getUpdatedAt());
    }
    private InterviewDetailResponse toInterview(InterviewSchedule interview) {
        return new InterviewDetailResponse(interview.getId(), interview.getApplication().getId(),
                interview.getApplication().getJob().getTitle(), interview.getScheduledAt(), interview.getMode(),
                interview.getLocationOrLink(), interview.getNotes(), interview.getFeedback(),
                interview.getStatus().name());
    }
    private void audit(String actor, String action, Long applicationId, Long offerId) {
        auditLogs.save(AuditLog.builder().actorEmail(actor).action(action).targetType("Offer")
                .targetId(String.valueOf(offerId)).details("Application " + applicationId)
                .createdAt(Instant.now()).build());
    }
    private void notifyStudent(Application application, String title, String message) {
        notifications.save(Notification.builder().user(application.getStudent()).title(title).message(message)
                .type("APPLICATION").readFlag(false).createdAt(Instant.now()).build());
    }
    private String normalizeOptionalForComparison(String value) {
        return value == null || value.isBlank() ? null : value;
    }
    private long countOfferStatus(List<Object[]> counts, Offer.Status status) {
        return counts.stream().filter(row -> status.equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue()).sum();
    }
    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    private void validateTransition(Status current, Status next) {
        if (next == Status.OFFERED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create an offer with package details through the offers API");
        if (current == Status.REJECTED || current == Status.OFFERED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Terminal application status cannot be changed");
        if (next == Status.OFFERED && current != Status.INTERVIEW_SCHEDULED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer requires a scheduled interview");
    }
    private CompanyResponse toCompany(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getIndustry(), company.getWebsite(),
                company.getLocation(), company.getDescription(), company.getLogoUrl(), company.isVerified());
    }
    private ApplicantResponse toApplicant(Application application) {
        StudentProfile profile = profiles.findByUserId(application.getStudent().getId()).orElse(null);
        List<String> rows = history.findByApplicationIdOrderByChangedAtAscIdAsc(application.getId()).stream()
                .map(row -> row.getChangedAt() + " " + row.getStatus()).toList();
        return new ApplicantResponse(application.getId(), application.getStudent().getId(),
                profile == null ? null : profile.getFullName(), application.getStudent().getEmail(),
                profile == null ? null : profile.getRollNumber(), profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getCgpa(), profile == null ? null : profile.getBacklogs(),
                profile == null ? null : profile.getSkills(), profile == null ? null : profile.getResumeUrl(),
                application.getStatus().name(), application.getAppliedAt(), rows);
    }
}
