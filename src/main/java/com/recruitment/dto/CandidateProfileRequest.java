package com.recruitment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CandidateProfileRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 30) String mobile,
        @NotBlank @Size(max = 160) String qualification,
        @NotNull @Min(1901) @Max(2155) Integer passingYear,
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal percentage,
        @NotNull @PositiveOrZero Integer backlogCount,
        @Size(max = 5000) String skills,
        @Size(max = 10000) String experience) {
}
