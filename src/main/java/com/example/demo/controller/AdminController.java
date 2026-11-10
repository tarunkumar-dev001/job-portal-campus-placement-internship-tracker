package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.admin.AdminDtos.*;
import com.example.demo.service.AdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/users")
    public List<UserResponse> users() { return service.users(); }

    @PatchMapping("/users/{userId}/status")
    public UserResponse status(Principal principal, @PathVariable Long userId,
                               @Valid @RequestBody UserStatusRequest request) {
        return service.setUserStatus(principal.getName(), userId, request);
    }

    @GetMapping("/colleges")
    public List<CollegeResponse> colleges() { return service.collegeList(); }

    @PostMapping("/colleges")
    @ResponseStatus(HttpStatus.CREATED)
    public CollegeResponse createCollege(Principal principal, @Valid @RequestBody CollegeRequest request) {
        return service.createCollege(principal.getName(), request);
    }

    @PatchMapping("/placement-officers/{userId}/college")
    public OfficerAssignmentResponse assignCollege(Principal principal, @PathVariable Long userId,
                                                    @Valid @RequestBody OfficerAssignmentRequest request) {
        return service.assignOfficerCollege(principal.getName(), userId, request);
    }

    @GetMapping("/companies")
    public List<CompanyResponse> companies() { return service.companies(); }

    @PostMapping("/companies/{ownerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(Principal principal, @PathVariable Long ownerId,
                                 @Valid @RequestBody CompanyRequest request) {
        return service.createCompany(principal.getName(), ownerId, request);
    }

    @PatchMapping("/companies/{companyId}/verification")
    public CompanyResponse verifyCompany(Principal principal, @PathVariable Long companyId,
                                 @Valid @RequestBody VerificationRequest request) {
        return service.verifyCompany(principal.getName(), companyId, request);
    }

    @GetMapping("/stats")
    public PlatformStats stats() { return service.stats(); }

    @GetMapping("/audit-logs")
    public List<AuditLogResponse> auditLogs() { return service.auditLogs(); }
}
