package com.recruitment.controller;

import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.CandidateProfileRequest;
import com.recruitment.dto.CandidateProfileResponse;
import com.recruitment.dto.CandidateNotificationResponse;
import com.recruitment.dto.CandidateResumeDownload;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.OfferPageResponse;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.CandidatePortalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/portal")
@Validated
@PreAuthorize("hasRole('CANDIDATE')")
public class CandidatePortalController {

    private final CandidatePortalService portalService;

    public CandidatePortalController(CandidatePortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/profile")
    public CandidateProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getProfile(principal.getUserId());
    }

    @PutMapping("/profile")
    public CandidateProfileResponse updateProfile(
            @Valid @RequestBody CandidateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.updateProfile(principal.getUserId(), request);
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadResume(
            @RequestParam("resume") MultipartFile resume,
            @AuthenticationPrincipal UserPrincipal principal) {
        portalService.uploadResume(principal.getUserId(), resume);
    }

    @GetMapping("/resume")
    public ResponseEntity<Resource> getResume(@AuthenticationPrincipal UserPrincipal principal) {
        CandidateResumeDownload download = portalService.getResume(principal.getUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.originalName()).build().toString())
                .body(download.resource());
    }

    @GetMapping("/jobs")
    public JobPageResponse getAvailableJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EmploymentType jobType,
            @RequestParam(required = false) @Min(1901) @Max(2155) Integer passingYear,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return portalService.getAvailableJobs(
                search, jobTitle, company, technology, location, jobType, passingYear, page, size);
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse getAvailableJob(@PathVariable @Min(1) long jobId) {
        return portalService.getAvailableJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/applications")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(
            @PathVariable @Min(1) long jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.apply(principal.getUserId(), jobId);
    }

    @GetMapping("/applications")
    public ApplicationPageResponse getApplications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getApplications(principal.getUserId(), page, size);
    }

    @GetMapping("/applications/{applicationId}/history")
    public java.util.List<ApplicationStatusHistoryResponse> getApplicationHistory(
            @PathVariable @Min(1) long applicationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getApplicationHistory(principal.getUserId(), applicationId);
    }

    @GetMapping("/interviews")
    public InterviewPageResponse getInterviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getInterviews(principal.getUserId(), page, size);
    }

    @GetMapping("/offers")
    public OfferPageResponse getOffers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getOffers(principal.getUserId(), page, size);
    }

    @GetMapping("/notifications")
    public java.util.List<CandidateNotificationResponse> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {
        return portalService.getNotifications(principal.getUserId());
    }
}
