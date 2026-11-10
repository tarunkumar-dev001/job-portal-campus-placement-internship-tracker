package com.example.demo.dto.admin;

import java.time.Instant;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.College;
import com.example.demo.entity.Company;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserResponse(Long id, String email, String role, boolean enabled, Instant createdAt) {}
    public record UserStatusRequest(boolean enabled) {}
    public record OfficerAssignmentRequest(Long collegeId) {}
    public record OfficerAssignmentResponse(Long userId, String email, String fullName, Long collegeId,
                                            String collegeName) {}
    public record CollegeRequest(String code, String name, String city, String state, String university, Boolean active) {}
    public record CollegeResponse(Long id, String code, String name, String city, String state,
                                  String university, boolean active) {
        public static CollegeResponse from(College college) {
            return new CollegeResponse(college.getId(), college.getCode(), college.getName(), college.getCity(),
                    college.getState(), college.getUniversity(), college.isActive());
        }
    }
    public record CompanyRequest(String name, String industry, String website, String location, String description) {}
    public record CompanyResponse(Long id, String name, String industry, String website, String location,
                                  String description, Long ownerUserId, boolean verified) {
        public static CompanyResponse from(Company company) {
            return new CompanyResponse(company.getId(), company.getName(), company.getIndustry(),
                    company.getWebsite(), company.getLocation(), company.getDescription(),
                    company.getOwnerUserId(), company.isVerified());
        }
    }
    public record AuditLogResponse(Long id, String actorEmail, String action, String targetType, String targetId,
                                   String details, Instant createdAt) {
        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorEmail(), log.getAction(), log.getTargetType(),
                    log.getTargetId(), log.getDetails(), log.getCreatedAt());
        }
    }
    public record VerificationRequest(boolean verified) {}
    public record PlatformStats(long users, long students, long recruiters, long placementOfficers,
                                long colleges, long companies, long drives, long selections) {}
}
