package com.recruitment.controller;

import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobRequest;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/jobs")
@Validated
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public JobResponse create(
            @Valid @RequestBody JobRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return jobService.create(request, principal.getUserId());
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public JobResponse getById(@PathVariable @Min(1) long jobId) {
        return jobService.getById(jobId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public JobPageResponse getJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EmploymentType jobType,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) @Min(1901) @Max(2155) Integer passingYear,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return jobService.getJobs(
                search, jobTitle, company, technology, location,
                jobType, status, passingYear, page, size);
    }

    @PutMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public JobResponse update(
            @PathVariable @Min(1) long jobId,
            @Valid @RequestBody JobRequest request) {
        return jobService.update(jobId, request);
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(@PathVariable @Min(1) long jobId) {
        jobService.delete(jobId);
    }
}
