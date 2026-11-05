package com.recruitment.controller;

import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.ApplicationStatusRequest;
import com.recruitment.dto.JobApplicationRequest;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.JobApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class JobApplicationController {

    private final JobApplicationService applicationService;

    public JobApplicationController(JobApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/jobs/{jobId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ApplicationResponse apply(
            @PathVariable @Min(1) long jobId,
            @Valid @RequestBody JobApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.apply(
                jobId,
                request.candidateId(),
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                hasRole(principal, "RECRUITER"));
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public ApplicationPageResponse getApplications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String candidate,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String job,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) @Min(1901) @Max(2155) Integer passingYear,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.getApplications(
                search, candidate, company, job, status, passingYear,
                principal.getUserId(), hasRole(principal, "RECRUITER"), page, size);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public ApplicationResponse getById(
            @PathVariable @Min(1) long applicationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.getById(
                applicationId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                hasRole(principal, "PLACEMENT_OFFICER"));
    }

    @GetMapping("/applications/{applicationId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public List<ApplicationStatusHistoryResponse> getHistory(
            @PathVariable @Min(1) long applicationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.getHistory(
                applicationId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                hasRole(principal, "PLACEMENT_OFFICER"));
    }

    @PutMapping("/applications/{applicationId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public ApplicationResponse updateStatus(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody ApplicationStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.updateStatus(
                applicationId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                hasRole(principal, "PLACEMENT_OFFICER"));
    }

    private static boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }
}
