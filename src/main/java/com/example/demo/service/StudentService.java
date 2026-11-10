package com.example.demo.service;

import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.io.UncheckedIOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.student.StudentDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.entity.DriveRegistration.RegistrationStatus;
import com.example.demo.entity.PlacementDrive.DriveStatus;

@Service
public class StudentService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final SavedJobRepository savedJobs;
    private final ApplicationStatusHistoryRepository history;
    private final InterviewScheduleRepository interviews;
    private final NotificationRepository notifications;
    private final DriveRegistrationRepository driveRegistrations;
    private final OfferRepository offers;
    private final AuditLogRepository auditLogs;
    private final PlacementDriveRepository placementDrives;
    private final Path storageDirectory;

    public StudentService(UserRepository users, StudentProfileRepository profiles, JobRepository jobs,
            ApplicationRepository applications, SavedJobRepository savedJobs,
            ApplicationStatusHistoryRepository history, InterviewScheduleRepository interviews,
            NotificationRepository notifications, DriveRegistrationRepository driveRegistrations,
            OfferRepository offers, AuditLogRepository auditLogs, PlacementDriveRepository placementDrives,
            @Value("${app.storage.resumes}") String storageDirectory) {
        this.users = users; this.profiles = profiles; this.jobs = jobs; this.applications = applications;
        this.savedJobs = savedJobs; this.history = history; this.interviews = interviews;
        this.notifications = notifications; this.driveRegistrations = driveRegistrations;
        this.offers = offers; this.auditLogs = auditLogs;
        this.placementDrives = placementDrives;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String email) {
        StudentProfile profile = profile(email);
        return toProfile(profile);
    }

    @Transactional
    public ProfileResponse saveProfile(String email, ProfileRequest request) {
        User user = user(email);
        StudentProfile profile = profiles.findByUserId(user.getId()).orElse(
                StudentProfile.builder().user(user).build());
        profile.setFullName(request.fullName().trim());
        profile.setRollNumber(request.rollNumber().trim());
        profile.setDepartment(request.department().trim());
        profile.setPhone(request.phone()); profile.setEducation(request.education());
        profile.setGraduationYear(request.graduationYear()); profile.setCurrentSemester(request.currentSemester());
        profile.setCgpa(request.cgpa()); profile.setBacklogs(request.backlogs());
        profile.setSkills(request.skills()); profile.setCertifications(request.certifications());
        profile.setProjects(request.projects()); profile.setExperience(request.experience());
        profile.setLinkedinUrl(request.linkedinUrl()); profile.setGithubUrl(request.githubUrl());
        profile.setPortfolioUrl(request.portfolioUrl());
        return toProfile(profiles.save(profile));
    }

    public Page<OpportunityResponse> opportunities(String email, String search, String location,
            String company, String skills, String jobType, Boolean internship, Boolean eligibleOnly, Pageable pageable) {
        StudentProfile profile = profile(email);
        Page<Job> page = jobs.findByDeadlineGreaterThanEqual(LocalDate.now(), pageable);
        List<Job> filtered = page.getContent().stream()
                .filter(job -> contains(search, job.getTitle(), job.getDescription(), job.getCompanyName()))
                .filter(job -> blank(location) || contains(location, job.getLocation()))
                .filter(job -> blank(company) || contains(company, job.getCompanyName()))
                .filter(job -> blank(skills) || contains(skills, job.getRequiredSkills()))
                .filter(job -> blank(jobType) || contains(jobType, job.getJobType()))
                .filter(job -> internship == null || Boolean.TRUE.equals(job.getInternship()) == internship)
                .filter(job -> !Boolean.TRUE.equals(eligibleOnly) || isEligible(profile, job)).toList();
        return new PageImpl<>(filtered.stream().map(job -> toOpportunity(job, profile, email)).toList(),
                pageable, filtered.size());
    }

    @Transactional
    public void saveJob(String email, Long jobId, boolean save) {
        User student = user(email);
        Job job = job(jobId);
        if (save) {
            if (savedJobs.findByStudentIdAndJobId(student.getId(), jobId).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is already saved");
            savedJobs.save(SavedJob.builder().student(student).job(job).savedAt(Instant.now()).build());
        } else {
            savedJobs.findByStudentIdAndJobId(student.getId(), jobId).ifPresent(savedJobs::delete);
        }
    }

    public List<OpportunityResponse> savedJobs(String email) {
        StudentProfile profile = profile(email);
        return savedJobs.findByStudentId(user(email).getId()).stream()
                .map(saved -> toOpportunity(saved.getJob(), profile, email)).toList();
    }

    @Transactional
    public ApplicationResponse apply(String email, Long jobId) {
        User student = user(email);
        if (applications.findByStudentIdAndJobId(student.getId(), jobId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application already exists");
        Job job = job(jobId);
        if (!job.isPublished() || job.isClosed())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not open for applications");
        if (job.getDeadline().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application deadline has passed");
        StudentProfile profile = profile(email);
        if (!isEligible(profile, job))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eligibility requirements are not met");
        Application application = Application.builder().student(student).job(job)
                .status(Application.Status.APPLIED).appliedAt(Instant.now()).build();
        application = applications.save(application);
        history.save(ApplicationStatusHistory.builder().application(application)
                .status(Application.Status.APPLIED).changedBy(email).changedAt(Instant.now())
                .note("Application submitted").build());
        return toApplication(application, email);
    }

    @Transactional
    public ApplicationResponse withdraw(String email, Long applicationId) {
        Application application = ownedApplication(email, applicationId);
        if (application.getStatus() != Application.Status.APPLIED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPLIED applications can be withdrawn");
        application.setWithdrawnAt(Instant.now());
        applications.save(application);
        return toApplication(application, email);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> applications(String email) {
        return applications.findByStudentId(user(email).getId()).stream()
                .map(application -> toApplication(application, email)).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewDetailResponse> interviews(String email) {
        return interviews.findByApplicationStudentId(user(email).getId()).stream().map(interview ->
                new InterviewDetailResponse(interview.getId(), interview.getApplication().getId(),
                        interview.getApplication().getJob().getTitle(), interview.getScheduledAt(), interview.getMode(),
                        interview.getLocationOrLink(), interview.getNotes(), interview.getFeedback(),
                        interview.getStatus().name())).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> offers(String email) {
        User student = user(email);
        return offers.findByApplicationStudentId(student.getId()).stream().map(this::toOffer).toList();
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveResponse> placementDrives(String email) {
        User student = user(email);
        return placementDrives.findByStatusAndRegistrationDeadlineGreaterThanEqualOrderByRegistrationDeadlineAsc(
                DriveStatus.OPEN, LocalDate.now()).stream().map(drive -> {
                    DriveRegistration registration = driveRegistrations.findByDriveIdAndStudentId(drive.getId(), student.getId()).orElse(null);
                    return new PlacementDriveResponse(drive.getId(), drive.getTitle(), drive.getCompanyName(),
                            drive.getJobTitle(), drive.getDriveType(), drive.getRegistrationDeadline(), drive.getDriveDate(),
                            drive.getStatus().name(), registration != null,
                            registration == null ? null : registration.getStatus().name());
                }).toList();
    }

    @Transactional
    public PlacementDriveResponse registerForDrive(String email, Long driveId) {
        User student = user(email);
        PlacementDrive drive = placementDrives.findByIdForUpdate(driveId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement drive not found"));
        if (drive.getStatus() != DriveStatus.OPEN || drive.getRegistrationDeadline().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Placement drive is not open for registration");
        if (driveRegistrations.findByDriveIdAndStudentId(driveId, student.getId()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this drive");
        DriveRegistration registration = driveRegistrations.save(DriveRegistration.builder().drive(drive).student(student)
                .status(RegistrationStatus.REGISTERED).registeredAt(Instant.now()).build());
        return new PlacementDriveResponse(drive.getId(), drive.getTitle(), drive.getCompanyName(), drive.getJobTitle(),
                drive.getDriveType(), drive.getRegistrationDeadline(), drive.getDriveDate(), drive.getStatus().name(),
                true, registration.getStatus().name());
    }

    @Transactional
    public OfferResponse respondToOffer(String email, Long offerId, OfferStatusRequest request) {
        Offer offer = offers.findByIdForUpdate(offerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (!offer.getApplication().getStudent().getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Offer belongs to another student");
        Offer.Status next;
        try { next = Offer.Status.valueOf(request.status().trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offer status"); }
        if (offer.getStatus() != Offer.Status.ISSUED || !Set.of(Offer.Status.ACCEPTED, Offer.Status.DECLINED).contains(next))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid offer status transition");
        offer.setStatus(next); offer.setUpdatedAt(Instant.now()); offers.save(offer);
        auditLogs.save(AuditLog.builder().actorEmail(email).action("OFFER_" + next.name())
                .targetType("Offer").targetId(String.valueOf(offerId)).details("Application " + offer.getApplication().getId())
                .createdAt(Instant.now()).build());
        return toOffer(offer);
    }

    public List<NotificationResponse> notifications(String email, int limit) {
        return notifications.findByUserIdOrderByCreatedAtDesc(user(email).getId(), PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(this::toNotification).toList();
    }

    public Resume uploadResume(String email, MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume must be a non-empty PDF below 5 MB");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF resumes are accepted");
        Files.createDirectories(storageDirectory);
        byte[] sample = file.getBytes();
        if (sample.length < 5 || sample[0] != '%' || sample[1] != 'P' || sample[2] != 'D' || sample[3] != 'F' || sample[4] != '-') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid PDF");
        }
        String storedName = user(email).getId() + "-" + UUID.randomUUID() + ".pdf";
        Path destination = storageDirectory.resolve(storedName);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        StudentProfile profile = profile(email);
        if (profile.getResumeUrl() != null) deleteStored(profile.getResumeUrl());
        profile.setResumeUrl(storedName);
        profiles.save(profile);
        return new Resume(storedName, file.getOriginalFilename(), file.getSize());
    }

    public ResumeFile downloadResume(String email) throws Exception {
        String storedName = profile(email).getResumeUrl();
        if (storedName == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        Path resume = storageDirectory.resolve(storedName).normalize();
        if (!resume.startsWith(storageDirectory)
                || !Files.isRegularFile(resume, LinkOption.NOFOLLOW_LINKS))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        return new ResumeFile("resume.pdf", Files.readAllBytes(resume));
    }

    @Transactional
    public void deleteResume(String email) {
        StudentProfile profile = profile(email);
        if (profile.getResumeUrl() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        deleteStored(profile.getResumeUrl());
        profile.setResumeUrl(null);
        profiles.save(profile);
    }

    public record Resume(String storedName, String originalName, long size) {}
    public record ResumeFile(String filename, byte[] content) {}

    private StudentProfile profile(String email) {
        return profiles.findByUserId(user(email).getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student profile not found"));
    }
    private User user(String email) {
        return users.findByEmail(email.toLowerCase()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
    private Job job(Long id) {
        return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }
    private Application ownedApplication(String email, Long id) {
        Application application = applications.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (!application.getStudent().getEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Application belongs to another student");
        return application;
    }
    private boolean isEligible(StudentProfile profile, Job job) {
        return (job.getMinCgpa() == null || profile.getCgpa() >= job.getMinCgpa())
                && (job.getMaxBacklogs() == null || profile.getBacklogs() <= job.getMaxBacklogs());
    }
    private OpportunityResponse toOpportunity(Job job, StudentProfile profile, String email) {
        boolean saved = savedJobs.findByStudentIdAndJobId(profile.getUser().getId(), job.getId()).isPresent();
        return new OpportunityResponse(job.getId(), job.getTitle(), job.getCompanyName(), job.getDescription(),
                job.getLocation(), job.getJobType(), job.getRequiredSkills(), job.getSalaryMin(), job.getSalaryMax(),
                job.getDeadline(), Boolean.TRUE.equals(job.getInternship()), isEligible(profile, job), saved);
    }
    private ProfileResponse toProfile(StudentProfile profile) {
        return new ProfileResponse(profile.getId(), profile.getUser().getEmail(), profile.getFullName(),
                profile.getRollNumber(), profile.getDepartment(), profile.getPhone(), profile.getEducation(),
                profile.getGraduationYear(), profile.getCurrentSemester(), profile.getCgpa(), profile.getBacklogs(),
                profile.getSkills(), profile.getCertifications(), profile.getProjects(), profile.getExperience(),
                profile.getLinkedinUrl(), profile.getGithubUrl(), profile.getPortfolioUrl(), profile.getResumeUrl());
    }
    private ApplicationResponse toApplication(Application application, String email) {
        List<StatusHistoryResponse> historyRows = history.findByApplicationIdOrderByChangedAtAsc(application.getId())
                .stream().map(row -> new StatusHistoryResponse(row.getStatus().name(), row.getChangedBy(),
                        row.getChangedAt(), row.getNote())).toList();
        InterviewSchedule interview = application.getId() == null ? null : null;
        InterviewResponse interviewResponse = interviews.findByApplicationStudentId(application.getStudent().getId()).stream()
                .filter(item -> item.getApplication().getId().equals(application.getId())).findFirst()
                .map(item -> new InterviewResponse(item.getId(), item.getScheduledAt(), item.getMode(),
                        item.getLocationOrLink(), item.getNotes(), item.getFeedback())).orElse(null);
        return new ApplicationResponse(application.getId(), application.getJob().getId(), application.getJob().getTitle(),
                application.getJob().getCompanyName(), application.getStatus().name(), application.getAppliedAt(),
                application.getRecruiterFeedback(), historyRows, interviewResponse);
    }
    private NotificationResponse toNotification(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getTitle(), notification.getMessage(),
                notification.getType(), notification.isReadFlag(), notification.getCreatedAt());
    }
    private void deleteStored(String name) {
        Path file = storageDirectory.resolve(name).normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored resume path is invalid");
        }
        try {
            Files.deleteIfExists(file);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Could not remove stored resume", exception);
        }
    }
    private OfferResponse toOffer(Offer offer) {
        Application application = offer.getApplication();
        return new OfferResponse(offer.getId(), application.getId(), application.getJob().getId(),
                application.getJob().getTitle(), offer.getPackageDetails(), offer.getPackageAmount(),
                offer.getJoiningDate(), offer.getStatus().name(), offer.getCreatedAt(), offer.getUpdatedAt());
    }
    private boolean contains(String expected, String... actual) {
        if (blank(expected)) return true;
        for (String value : actual) if (value != null && value.toLowerCase().contains(expected.toLowerCase())) return true;
        return false;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
