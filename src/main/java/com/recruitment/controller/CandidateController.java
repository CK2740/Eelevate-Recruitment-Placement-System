package com.recruitment.controller;

import com.recruitment.dto.CandidatePageResponse;
import com.recruitment.dto.CandidateRequest;
import com.recruitment.dto.CandidateResponse;
import com.recruitment.dto.CandidateStatus;
import com.recruitment.dto.CandidateStatusRequest;
import com.recruitment.dto.CandidateResumeDownload;
import com.recruitment.service.CandidatePortalService;
import com.recruitment.service.CandidateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidates")
@Validated
public class CandidateController {

    private final CandidateService candidateService;
    private final CandidatePortalService candidatePortalService;

    public CandidateController(
            CandidateService candidateService,
            CandidatePortalService candidatePortalService) {
        this.candidateService = candidateService;
        this.candidatePortalService = candidatePortalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CandidateResponse create(@Valid @RequestBody CandidateRequest request) {
        return candidateService.create(request);
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CandidateResponse getById(@PathVariable @Min(1) long candidateId) {
        return candidateService.getById(candidateId);
    }

    @GetMapping("/{candidateId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<Resource> getResume(@PathVariable @Min(1) long candidateId) {
        CandidateResumeDownload download = candidatePortalService.getCandidateResume(candidateId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.originalName()).build().toString())
                .body(download.resource());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CandidatePageResponse getCandidates(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) @Min(1901) @Max(2155) Integer passingYear,
            @RequestParam(required = false) CandidateStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return candidateService.getCandidates(
                search,
                name,
                email,
                technology,
                passingYear,
                status,
                page,
                size);
    }

    @PutMapping("/{candidateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CandidateResponse update(
            @PathVariable @Min(1) long candidateId,
            @Valid @RequestBody CandidateRequest request) {
        return candidateService.update(candidateId, request);
    }

    @PatchMapping("/{candidateId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CandidateResponse updateStatus(
            @PathVariable @Min(1) long candidateId,
            @Valid @RequestBody CandidateStatusRequest request) {
        return candidateService.updateStatus(candidateId, request.status());
    }

    @DeleteMapping("/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(@PathVariable @Min(1) long candidateId) {
        candidateService.delete(candidateId);
    }
}
