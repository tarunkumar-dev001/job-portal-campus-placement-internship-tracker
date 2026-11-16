package com.example.demo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.admin.AdminDtos.*;
import com.example.demo.entity.*;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.*;

@Service
public class AdminService {
    private final UserRepository users;
    private final CollegeRepository colleges;
    private final CompanyRepository companies;
    private final PlacementOfficerProfileRepository officers;
    private final PlacementDriveRepository drives;
    private final PlacementSelectionRepository selections;
    private final AuditLogRepository auditLogs;
    private final RefreshTokenRepository refreshTokens;

    public AdminService(UserRepository users, CollegeRepository colleges, CompanyRepository companies,
            PlacementOfficerProfileRepository officers, PlacementDriveRepository drives,
            PlacementSelectionRepository selections, AuditLogRepository auditLogs,
            RefreshTokenRepository refreshTokens) {
        this.users = users; this.colleges = colleges; this.companies = companies;
        this.officers = officers; this.drives = drives; this.selections = selections; this.auditLogs = auditLogs;
        this.refreshTokens = refreshTokens;
    }

    public List<UserResponse> users() {
        return users.findAll().stream().map(this::toUser).toList();
    }

    @Transactional
    public UserResponse setUserStatus(String actor, Long userId, UserStatusRequest request) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setEnabled(request.enabled());
        if (!request.enabled()) {
            Instant revokedAt = Instant.now();
            refreshTokens.findActiveByUserIdForUpdate(userId)
                    .forEach(token -> token.setRevokedAt(revokedAt));
        }
        audit(actor, "USER_STATUS_CHANGED", "User", String.valueOf(userId), Boolean.toString(request.enabled()));
        return toUser(users.save(user));
    }

    public CollegeResponse createCollege(String actor, CollegeRequest request) {
        College college = College.builder().code(request.code().trim().toUpperCase()).name(request.name().trim())
                .city(request.city()).state(request.state()).university(request.university())
                .active(request.active() == null || request.active()).build();
        College saved = colleges.save(college);
        audit(actor, "COLLEGE_CREATED", "College", String.valueOf(saved.getId()), saved.getName());
        return CollegeResponse.from(saved);
    }

    public List<CollegeResponse> collegeList() {
        return colleges.findAll().stream().map(CollegeResponse::from).toList();
    }

    @Transactional
    public OfficerAssignmentResponse assignOfficerCollege(String actor, Long officerUserId, OfficerAssignmentRequest request) {
        PlacementOfficerProfile officer = officers.findByUserId(officerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement officer profile not found"));
        College college = colleges.findById(request.collegeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "College not found"));
        officer.setCollege(college);
        audit(actor, "OFFICER_COLLEGE_ASSIGNED", "PlacementOfficer", String.valueOf(officerUserId),
                String.valueOf(college.getId()));
        officers.save(officer);
        return new OfficerAssignmentResponse(officer.getUser().getId(), officer.getUser().getEmail(),
                officer.getFullName(), college.getId(), college.getName());
    }

    public CompanyResponse createCompany(String actor, Long ownerId, CompanyRequest request) {
        User owner = user(ownerId);
        if (owner.getRole() != Role.RECRUITER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company owner must be a recruiter");
        Company company = Company.builder().name(request.name().trim()).industry(request.industry())
                .website(request.website()).location(request.location()).description(request.description())
                .ownerUserId(ownerId).verified(false).build();
        Company saved = companies.save(company);
        audit(actor, "COMPANY_CREATED", "Company", String.valueOf(saved.getId()), saved.getName());
        return CompanyResponse.from(saved);
    }

    public List<CompanyResponse> companies() {
        return companies.findAll().stream().map(CompanyResponse::from).toList();
    }

    public CompanyResponse verifyCompany(String actor, Long companyId, VerificationRequest request) {
        Company company = companies.findById(companyId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        company.setVerified(request.verified());
        audit(actor, "COMPANY_VERIFICATION_CHANGED", "Company", String.valueOf(companyId), Boolean.toString(request.verified()));
        return CompanyResponse.from(companies.save(company));
    }

    public PlatformStats stats() {
        List<User> allUsers = users.findAll();
        long students = allUsers.stream().filter(user -> user.getRole() == Role.STUDENT).count();
        long recruiters = allUsers.stream().filter(user -> user.getRole() == Role.RECRUITER).count();
        long officers = allUsers.stream().filter(user -> user.getRole() == Role.PLACEMENT_OFFICER).count();
        return new PlatformStats(allUsers.size(), students, recruiters, officers, colleges.count(), companies.count(), drives.count(),
                selections.count());
    }

    public List<AuditLogResponse> auditLogs() {
        return auditLogs.findAll().stream().map(AuditLogResponse::from).toList();
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
    private void audit(String actor, String action, String type, String id, String details) {
        auditLogs.save(AuditLog.builder().actorEmail(actor).action(action).targetType(type).targetId(id)
                .details(details).createdAt(Instant.now()).build());
    }
    private UserResponse toUser(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.isEnabled(), null);
    }
}
