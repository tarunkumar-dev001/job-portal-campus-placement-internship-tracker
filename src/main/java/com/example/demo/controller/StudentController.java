package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.student.StudentDtos.*;
import com.example.demo.service.StudentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final StudentService service;

    public StudentController(StudentService service) { this.service = service; }

    @GetMapping("/profile")
    public ProfileResponse profile(Principal principal) { return service.getProfile(principal.getName()); }

    @PutMapping("/profile")
    public ProfileResponse saveProfile(Principal principal, @Valid @RequestBody ProfileRequest request) {
        return service.saveProfile(principal.getName(), request);
    }

    @GetMapping("/opportunities")
    public Page<OpportunityResponse> opportunities(Principal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Boolean internship,
            @RequestParam(required = false) Boolean eligibleOnly,
            @PageableDefault(size = 20, sort = "deadline", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.opportunities(principal.getName(), search, location, company, skills, jobType,
                internship, eligibleOnly, pageable);
    }

    @PutMapping("/saved-jobs/{jobId}")
    public void saveJob(Principal principal, @PathVariable Long jobId) {
        service.saveJob(principal.getName(), jobId, true);
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    public void removeSavedJob(Principal principal, @PathVariable Long jobId) {
        service.saveJob(principal.getName(), jobId, false);
    }

    @GetMapping("/saved-jobs")
    public List<OpportunityResponse> savedJobs(Principal principal) {
        return service.savedJobs(principal.getName());
    }

    @PostMapping("/applications/{jobId}")
    public ApplicationResponse apply(Principal principal, @PathVariable Long jobId) {
        return service.apply(principal.getName(), jobId);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications(Principal principal) {
        return service.applications(principal.getName());
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ApplicationResponse withdraw(Principal principal, @PathVariable Long applicationId) {
        return service.withdraw(principal.getName(), applicationId);
    }

    @GetMapping("/interviews")
    public List<InterviewDetailResponse> interviews(Principal principal) {
        return service.interviews(principal.getName());
    }

    @GetMapping("/offers")
    public List<OfferResponse> offers(Principal principal) { return service.offers(principal.getName()); }

    @GetMapping("/placement-drives")
    public List<PlacementDriveResponse> placementDrives(Principal principal) {
        return service.placementDrives(principal.getName());
    }

    @PostMapping("/placement-drives/{driveId}/registration")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementDriveResponse registerForDrive(Principal principal, @PathVariable Long driveId) {
        return service.registerForDrive(principal.getName(), driveId);
    }

    @PatchMapping("/offers/{offerId}/status")
    public OfferResponse respondToOffer(Principal principal, @PathVariable Long offerId,
                                        @Valid @RequestBody OfferStatusRequest request) {
        return service.respondToOffer(principal.getName(), offerId, request);
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications(Principal principal,
                                                    @RequestParam(defaultValue = "20") int limit) {
        return service.notifications(principal.getName(), limit);
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentService.Resume uploadResume(Principal principal, @RequestParam("file") MultipartFile file)
            throws Exception {
        return service.uploadResume(principal.getName(), file);
    }

    @GetMapping("/resume")
    public ResponseEntity<byte[]> downloadResume(Principal principal) throws Exception {
        StudentService.ResumeFile resume = service.downloadResume(principal.getName());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resume.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF).body(resume.content());
    }

    @DeleteMapping("/resume")
    public void deleteResume(Principal principal) { service.deleteResume(principal.getName()); }
}
