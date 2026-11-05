package com.recruitment.controller;

import com.recruitment.dto.CompanyPageResponse;
import com.recruitment.dto.CompanyRequest;
import com.recruitment.dto.CompanyResponse;
import com.recruitment.dto.CompanyStatus;
import com.recruitment.service.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/companies")
@Validated
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyResponse create(@Valid @RequestBody CompanyRequest request) {
        return companyService.create(request);
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public CompanyResponse getById(@PathVariable @Min(1) long companyId) {
        return companyService.getById(companyId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public CompanyPageResponse getCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return companyService.getCompanies(search, name, industry, location, status, page, size);
    }

    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public CompanyResponse update(
            @PathVariable @Min(1) long companyId,
            @Valid @RequestBody CompanyRequest request) {
        return companyService.update(companyId, request);
    }

    @DeleteMapping("/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(@PathVariable @Min(1) long companyId) {
        companyService.delete(companyId);
    }
}
