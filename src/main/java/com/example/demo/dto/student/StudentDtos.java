package com.example.demo.dto.student;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.*;

public final class StudentDtos {
    private StudentDtos() {}

    public record ProfileRequest(@NotBlank String fullName, @NotBlank String rollNumber,
                                 @NotBlank String department, String phone, String education,
                                 @NotNull @Min(2020) @Max(2040) Integer graduationYear,
                                 Integer currentSemester, @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double cgpa,
                                 @NotNull @Min(0) Integer backlogs, String skills, String certifications,
                                 String projects, String experience, String linkedinUrl, String githubUrl,
                                 String portfolioUrl) {}
    public record ProfileResponse(Long id, String email, String fullName, String rollNumber, String department,
                                  String phone, String education, Integer graduationYear, Integer currentSemester,
                                  Double cgpa, Integer backlogs, String skills, String certifications, String projects,
                                  String experience, String linkedinUrl, String githubUrl, String portfolioUrl,
                                  String resumeUrl) {}
    public record OpportunityResponse(Long id, String title, String companyName, String description,
                                       String location, String jobType, String requiredSkills, Double salaryMin,
                                       Double salaryMax, LocalDate deadline, boolean internship, boolean eligible,
                                       boolean saved) {}
    public record ApplicationResponse(Long id, Long jobId, String jobTitle, String companyName,
                                      String status, Instant appliedAt, String recruiterFeedback,
                                      Instant withdrawnAt, List<StatusHistoryResponse> history,
                                      InterviewResponse interview) {}
    public record StatusHistoryResponse(String status, String changedBy, Instant changedAt,
                                        String note, String rejectionExplanation) {}
    public record InterviewResponse(Long id, Instant scheduledAt, String mode, String locationOrLink,
                                    String notes, String feedback) {}
    public record NotificationResponse(Long id, String title, String message, String type,
                                       boolean readFlag, Instant createdAt) {}
    public record OfferResponse(Long id, Long applicationId, Long jobId, String jobTitle,
                                String packageDetails, Double packageAmount, Instant joiningDate,
                                String status, Instant createdAt, Instant updatedAt) {}
    public record OfferStatusRequest(@NotBlank String status) {}
    public record InterviewDetailResponse(Long id, Long applicationId, String jobTitle, Instant scheduledAt,
                                          String mode, String locationOrLink, String notes, String feedback,
                                          String status) {}
    public record PlacementDriveResponse(Long id, String title, String companyName, String jobTitle,
                                         String driveType, LocalDate registrationDeadline, LocalDate driveDate,
                                         String status, boolean registered, String registrationStatus) {}
}
