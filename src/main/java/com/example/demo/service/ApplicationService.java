package com.example.demo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatusHistory;
import com.example.demo.entity.Application.Status;
import com.example.demo.entity.Job;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.dto.LegacyApplicationResponse;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.ApplicationStatusHistoryRepository;
import com.example.demo.repository.JobRepository;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.UserRepository;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final JobRepository jobRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository historyRepository,
            UserRepository userRepository, StudentProfileRepository studentProfileRepository,
            JobRepository jobRepository) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public LegacyApplicationResponse apply(Long jobId, String studentEmail) {
        User student = getUser(studentEmail);
        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can apply");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.isPublished() || job.isClosed()
                || job.getDeadline().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not open for applications");
        }
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student profile not found"));

        if (profile.getCgpa() < job.getMinCgpa()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CGPA requirement is not met");
        }
        if (profile.getBacklogs() > job.getMaxBacklogs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum backlog limit is exceeded");
        }
        if (applicationRepository.findByStudentIdAndJobId(student.getId(), jobId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Application already exists for this job");
        }

        Application application = Application.builder()
                .student(student)
                .job(job)
                .status(Status.APPLIED)
                .appliedAt(Instant.now())
                .build();
        Application saved = applicationRepository.save(application);
        historyRepository.save(ApplicationStatusHistory.builder().application(saved).status(Status.APPLIED)
                .changedBy(studentEmail).changedAt(Instant.now()).note("Application submitted").build());
        return LegacyApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LegacyApplicationResponse> getStudentApplications(String studentEmail) {
        User student = getUser(studentEmail);
        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only students can view their applications");
        }
        return applicationRepository.findByStudentId(student.getId()).stream()
                .map(LegacyApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LegacyApplicationResponse> getRecruiterApplications(String recruiterEmail) {
        User recruiter = getUser(recruiterEmail);
        if (recruiter.getRole() != Role.RECRUITER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only recruiters can view applicants");
        }
        return jobRepository.findByRecruiterId(recruiter.getId()).stream()
                .flatMap(job -> applicationRepository.findByJobId(job.getId()).stream())
                .map(LegacyApplicationResponse::from)
                .toList();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
