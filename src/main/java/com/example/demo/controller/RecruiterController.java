package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.recruiter.RecruiterDtos.*;
import com.example.demo.dto.DashboardStatsDtos.RecruiterDashboardStats;
import com.example.demo.service.RecruiterService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {
    private final RecruiterService service;

    public RecruiterController(RecruiterService service) { this.service = service; }

    @GetMapping("/stats")
    public RecruiterDashboardStats stats(Principal principal) {
        return service.dashboardStats(principal.getName());
    }

    @GetMapping("/company")
    public CompanyResponse company(Principal principal) { return service.company(principal.getName()); }

    @PostMapping("/company")
    public CompanyResponse saveCompany(Principal principal, @Valid @RequestBody CompanyRequest request) {
        return service.saveCompany(principal.getName(), request);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(Principal principal, @Valid @RequestBody JobRequest request) {
        return JobResponse.from(service.createJob(principal.getName(), request));
    }

    @PutMapping("/jobs/{jobId}")
    public JobResponse updateJob(Principal principal, @PathVariable Long jobId, @Valid @RequestBody JobRequest request) {
        return JobResponse.from(service.updateJob(principal.getName(), jobId, request));
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs(Principal principal) {
        return service.jobs(principal.getName()).stream().map(JobResponse::from).toList();
    }

    @PostMapping("/jobs/{jobId}/publish")
    public JobResponse publishJob(Principal principal, @PathVariable Long jobId) {
        return JobResponse.from(service.publishJob(principal.getName(), jobId));
    }

    @PostMapping("/jobs/{jobId}/close")
    public JobResponse closeJob(Principal principal, @PathVariable Long jobId) {
        return JobResponse.from(service.closeJob(principal.getName(), jobId));
    }

    @DeleteMapping("/jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(Principal principal, @PathVariable Long jobId) { service.deleteJob(principal.getName(), jobId); }

    @GetMapping("/jobs/{jobId}/applications")
    public List<ApplicantResponse> applicants(Principal principal, @PathVariable Long jobId) {
        return service.applicants(principal.getName(), jobId);
    }

    @PatchMapping("/applications/{applicationId}/status")
    public ApplicantResponse status(Principal principal, @PathVariable Long applicationId, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(principal.getName(), applicationId, request);
    }

    @PostMapping("/applications/{applicationId}/interview")
    public InterviewDetailResponse scheduleInterview(Principal principal, @PathVariable Long applicationId, @Valid @RequestBody InterviewRequest request) {
        return service.scheduleInterview(principal.getName(), applicationId, request);
    }

    @GetMapping("/interviews")
    public List<InterviewDetailResponse> interviews(Principal principal) { return service.interviews(principal.getName()); }

    @PutMapping("/applications/{applicationId}/interviews/{interviewId}")
    public InterviewDetailResponse reschedule(Principal principal, @PathVariable Long applicationId,
            @PathVariable Long interviewId, @Valid @RequestBody InterviewRequest request) {
        return service.rescheduleInterview(principal.getName(), applicationId, interviewId, request);
    }

    @PostMapping("/applications/{applicationId}/interviews/{interviewId}/cancel")
    public InterviewDetailResponse cancel(Principal principal, @PathVariable Long applicationId, @PathVariable Long interviewId) {
        return service.cancelInterview(principal.getName(), applicationId, interviewId);
    }

    @PostMapping("/applications/{applicationId}/interviews/{interviewId}/feedback")
    public InterviewDetailResponse feedback(Principal principal, @PathVariable Long applicationId, @PathVariable Long interviewId,
                           @Valid @RequestBody FeedbackRequest request) {
        return service.submitFeedback(principal.getName(), applicationId, interviewId, request);
    }

    @GetMapping("/applications/{applicationId}/resume")
    public ResponseEntity<byte[]> resume(Principal principal, @PathVariable Long applicationId) throws Exception {
        byte[] resume = service.downloadResume(principal.getName(), applicationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resume.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resume);
    }

    @PostMapping("/applications/{applicationId}/offer")
    public OfferResponse offer(Principal principal, @PathVariable Long applicationId,
                               @Valid @RequestBody OfferRequest request) {
        return service.createOffer(principal.getName(), applicationId, request);
    }

    @PostMapping("/applications/{applicationId}/offers")
    public OfferResponse createOffer(Principal principal, @PathVariable Long applicationId,
                                     @Valid @RequestBody OfferRequest request) {
        return service.createOffer(principal.getName(), applicationId, request);
    }

    @GetMapping("/offers")
    public List<OfferResponse> offers(Principal principal) { return service.recruiterOffers(principal.getName()); }

    @PatchMapping("/offers/{offerId}/status")
    public OfferResponse offerStatus(Principal principal, @PathVariable Long offerId,
                                     @Valid @RequestBody OfferStatusRequest request) {
        return service.updateOfferStatus(principal.getName(), offerId, request);
    }
}
