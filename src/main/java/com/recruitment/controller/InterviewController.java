package com.recruitment.controller;

import com.recruitment.dto.InterviewMode;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.dto.InterviewRescheduleRequest;
import com.recruitment.dto.InterviewResponse;
import com.recruitment.dto.InterviewStatus;
import com.recruitment.dto.InterviewStatusHistoryResponse;
import com.recruitment.dto.InterviewStatusRequest;
import com.recruitment.dto.InterviewerFeedbackSubmission;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.InterviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/applications/{applicationId}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewResponse schedule(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody InterviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (request.applicationId() != applicationId) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The application ID in the path and request must match");
        }
        return interviewService.schedule(
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewPageResponse getInterviews(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String candidate,
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) InterviewMode mode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.getInterviews(
                search,
                candidate,
                job,
                company,
                status,
                mode,
                from,
                to,
                principal.getUserId(),
                hasRole(principal, "RECRUITER"),
                page,
                size);
    }

    @GetMapping("/interviews/{interviewId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewResponse getById(
            @PathVariable @Min(1) long interviewId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.getById(
                interviewId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @PutMapping("/interviews/{interviewId}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewResponse reschedule(
            @PathVariable @Min(1) long interviewId,
            @Valid @RequestBody InterviewRescheduleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.reschedule(
                interviewId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"));
    }

    @PatchMapping("/interviews/{interviewId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewResponse updateStatus(
            @PathVariable @Min(1) long interviewId,
            @Valid @RequestBody InterviewStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.updateStatus(
                interviewId,
                request.status(),
                request.reason(),
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @PutMapping("/interviews/{interviewId}/feedback")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public InterviewResponse submitFeedback(
            @PathVariable @Min(1) long interviewId,
            @RequestBody @NotEmpty List<@Valid InterviewerFeedbackSubmission> submissions,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.submitFeedback(
                interviewId,
                submissions,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @GetMapping("/interviews/{interviewId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<InterviewStatusHistoryResponse> getHistory(
            @PathVariable @Min(1) long interviewId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return interviewService.getHistory(
                interviewId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    private static boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }
}
