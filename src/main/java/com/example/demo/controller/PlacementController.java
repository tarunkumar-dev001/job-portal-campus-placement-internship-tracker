package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.placement.*;
import com.example.demo.entity.PlacementDrive.DriveStatus;
import com.example.demo.service.PlacementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/placement")
public class PlacementController {
    private final PlacementService service;

    public PlacementController(PlacementService service) { this.service = service; }

    @GetMapping("/colleges")
    public List<CollegeResponse> colleges(Principal principal) {
        return service.colleges(principal.getName());
    }

    @GetMapping("/colleges/{collegeId}")
    public CollegeResponse college(Principal principal, @PathVariable Long collegeId) {
        return service.college(principal.getName(), collegeId);
    }

    @GetMapping("/colleges/{collegeId}/departments")
    public List<DepartmentResponse> departments(Principal principal, @PathVariable Long collegeId) {
        return service.departments(principal.getName(), collegeId);
    }

    @PostMapping("/colleges/{collegeId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse createDepartment(Principal principal, @PathVariable Long collegeId,
                                                @Valid @RequestBody DepartmentRequest request) {
        return service.createDepartment(principal.getName(), collegeId, request);
    }

    @GetMapping("/drives")
    public List<DriveResponse> drives(Principal principal) { return service.drives(principal.getName()); }

    @PostMapping("/drives")
    @ResponseStatus(HttpStatus.CREATED)
    public DriveResponse createDrive(Principal principal, @Valid @RequestBody DriveRequest request) {
        return service.createDrive(principal.getName(), request);
    }

    @PatchMapping("/drives/{driveId}/status")
    public DriveResponse updateStatus(Principal principal, @PathVariable Long driveId,
                                      @RequestParam DriveStatus status) {
        return service.updateDriveStatus(principal.getName(), driveId, status);
    }

    @GetMapping("/drives/{driveId}/registrations")
    public List<DriveRegistrationResponse> registrations(Principal principal, @PathVariable Long driveId) {
        return service.registrations(principal.getName(), driveId);
    }

    @PatchMapping("/drives/{driveId}/registrations/{studentId}/eligibility")
    public DriveRegistrationResponse verifyEligibility(Principal principal, @PathVariable Long driveId,
                                                        @PathVariable Long studentId) {
        return service.verifyEligibility(principal.getName(), driveId, studentId);
    }

    @PostMapping("/drives/{driveId}/selections")
    @ResponseStatus(HttpStatus.CREATED)
    public SelectionResponse select(Principal principal, @PathVariable Long driveId,
                                    @Valid @RequestBody SelectionRequest request) {
        return service.selectStudent(principal.getName(), driveId, request);
    }

    @PostMapping("/students/import")
    public StudentImportResponse importStudents(Principal principal, @RequestParam("file") MultipartFile file) {
        return service.importStudents(principal.getName(), file);
    }

    @GetMapping("/stats")
    public PlacementStatsResponse stats(Principal principal) {
        return service.stats(principal.getName());
    }
}
