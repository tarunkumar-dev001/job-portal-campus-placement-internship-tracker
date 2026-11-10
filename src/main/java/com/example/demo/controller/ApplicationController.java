package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.LegacyApplicationResponse;
import com.example.demo.dto.recruiter.RecruiterDtos.ApplicantResponse;
import com.example.demo.dto.recruiter.RecruiterDtos.StatusRequest;
import com.example.demo.entity.Application.Status;
import com.example.demo.service.ApplicationService;
import com.example.demo.service.RecruiterService;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final RecruiterService recruiterService;

    public ApplicationController(ApplicationService applicationService, RecruiterService recruiterService) {
        this.applicationService = applicationService;
        this.recruiterService = recruiterService;
    }

    @GetMapping("/student")
    public List<LegacyApplicationResponse> studentApplications(Principal principal) {
        return applicationService.getStudentApplications(principal.getName());
    }

    @GetMapping("/recruiter")
    public List<LegacyApplicationResponse> recruiterApplications(Principal principal) {
        return applicationService.getRecruiterApplications(principal.getName());
    }

    @PostMapping("/apply/{jobId}")
    @ResponseStatus(HttpStatus.CREATED)
    public LegacyApplicationResponse apply(@PathVariable Long jobId, Principal principal) {
        return applicationService.apply(jobId, principal.getName());
    }

    @PatchMapping("/{applicationId}/status")
    public ApplicantResponse updateStatus(@PathVariable Long applicationId,
            @RequestParam Status status, Principal principal) {
        return recruiterService.updateStatus(principal.getName(), applicationId,
                new StatusRequest(status.name(), null));
    }
}
