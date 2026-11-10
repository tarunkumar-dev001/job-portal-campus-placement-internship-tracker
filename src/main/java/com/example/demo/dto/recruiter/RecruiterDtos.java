package com.example.demo.dto.recruiter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.*;

public final class RecruiterDtos {
    private RecruiterDtos() {}
    public record JobResponse(Long id, String title, String description, String companyName, Double minCgpa,
                              Integer maxBacklogs, LocalDate deadline, String location, String jobType,
                              String requiredSkills, Double salaryMin, Double salaryMax, Boolean internship,
                              boolean published, boolean closed) {
        public static JobResponse from(com.example.demo.entity.Job job) {
            return new JobResponse(job.getId(), job.getTitle(), job.getDescription(), job.getCompanyName(),
                    job.getMinCgpa(), job.getMaxBacklogs(), job.getDeadline(), job.getLocation(),
                    job.getJobType(), job.getRequiredSkills(), job.getSalaryMin(), job.getSalaryMax(),
                    job.getInternship(), job.isPublished(), job.isClosed());
        }
    }
    public record CompanyRequest(@NotBlank String name, String industry, String website, String location,
                                 String description, String logoUrl) {}
    public record CompanyResponse(Long id, String name, String industry, String website, String location,
                                  String description, String logoUrl, boolean verified) {}
    public record JobRequest(@NotBlank String title, @NotBlank String description,
                             @NotBlank String companyName, @DecimalMin("0.0") Double minCgpa,
                             @Min(0) Integer maxBacklogs, @NotNull LocalDate deadline, String location,
                             String jobType, String requiredSkills, Double salaryMin, Double salaryMax,
                             Boolean internship) {}
    public record ApplicantResponse(Long applicationId, Long studentUserId, String studentName, String email,
                                    String rollNumber, String department, Double cgpa, Integer backlogs,
                                    String skills, String resumeUrl, String status, Instant appliedAt,
                                    List<String> history) {}
    public record InterviewRequest(@NotNull Instant scheduledAt, @NotBlank String mode,
                                   String locationOrLink, String notes) {}
    public record InterviewResponse(Long id, Instant scheduledAt, String mode, String locationOrLink,
                                    String notes, String feedback) {}
    public record InterviewDetailResponse(Long id, Long applicationId, String jobTitle, Instant scheduledAt,
                                          String mode, String locationOrLink, String notes, String feedback,
                                          String status) {}
    public record FeedbackRequest(@NotBlank String feedback) {}
    public record StatusRequest(@NotNull String status, String note) {}
    public record OfferRequest(String packageDetails, @DecimalMin("0.0") Double packageAmount,
                               @NotNull Instant joiningDate) {}
    public record OfferResponse(Long id, Long applicationId, String packageDetails, Double packageAmount,
                                Instant joiningDate, String status, Instant createdAt, Instant updatedAt) {}
    public record OfferStatusRequest(@NotBlank String status) {}
}
