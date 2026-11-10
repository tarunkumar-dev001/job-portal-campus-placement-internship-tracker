package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.entity.Job;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.Company;
import com.example.demo.dto.recruiter.RecruiterDtos.JobRequest;
import com.example.demo.repository.JobRepository;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.CompanyRepository;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CompanyRepository companyRepository;

    public JobService(JobRepository jobRepository, UserRepository userRepository,
            StudentProfileRepository studentProfileRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.companyRepository = companyRepository;
    }

    public Job create(JobRequest request, String recruiterEmail) {
        User recruiter = getUser(recruiterEmail);
        if (recruiter.getRole() != Role.RECRUITER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only recruiters can create jobs");
        }
        Company company = companyRepository.findByOwnerUserId(recruiter.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Company profile required"));
        if (!company.isVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company verification required");
        }
        Job job = Job.builder()
                .title(request.title())
                .description(request.description())
                .companyName(request.companyName())
                .minCgpa(request.minCgpa())
                .maxBacklogs(request.maxBacklogs())
                .deadline(request.deadline())
                .location(request.location())
                .jobType(request.jobType())
                .requiredSkills(request.requiredSkills())
                .salaryMin(request.salaryMin())
                .salaryMax(request.salaryMax())
                .internship(request.internship())
                .build();
        job.setRecruiterId(recruiter.getId());
        job.setPublished(false);
        job.setClosed(false);
        return jobRepository.save(job);
    }

    public List<Job> getOpenJobs() {
        return jobRepository.findByPublishedTrueAndClosedFalseAndDeadlineGreaterThanEqual(LocalDate.now());
    }

    public List<Job> getEligibleJobs(String studentEmail) {
        User student = getUser(studentEmail);
        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can view eligible jobs");
        }
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student profile not found"));

        return jobRepository.findByMinCgpaLessThanEqual(profile.getCgpa()).stream()
                .filter(Job::isPublished)
                .filter(job -> !job.isClosed())
                .filter(job -> job.getDeadline().isAfter(LocalDate.now())
                        || job.getDeadline().isEqual(LocalDate.now()))
                .filter(job -> job.getMaxBacklogs() >= profile.getBacklogs())
                .toList();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
