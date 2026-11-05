package com.recruitment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record JobRequest(
        @NotNull @Positive Long companyId,
        @NotBlank @Size(max = 180) String jobTitle,
        @NotBlank @Size(max = 180) String technology,
        @NotNull @Positive Integer openings,
        @NotBlank @Size(max = 200) String location,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal packageAmount,
        @Size(max = 10000) String jobDescription,
        @Size(max = 5000) String skills,
        @NotNull @FutureOrPresent LocalDate applicationDeadline,
        @NotNull EmploymentType employmentType,
        JobStatus status,
        @NotNull @Valid JobEligibilityRequest eligibilityCriteria) {
}
