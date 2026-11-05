package com.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobResponse(
        long jobId,
        long companyId,
        String companyName,
        String jobTitle,
        String technology,
        int openings,
        String location,
        BigDecimal packageAmount,
        String jobDescription,
        String skills,
        String requiredQualification,
        LocalDate applicationDeadline,
        EmploymentType employmentType,
        JobStatus status,
        JobEligibilityRequest eligibilityCriteria,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
