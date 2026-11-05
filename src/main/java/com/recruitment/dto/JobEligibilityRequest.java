package com.recruitment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record JobEligibilityRequest(
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal minimumPercentage,
        @Min(1901) @Max(2155) Integer minimumPassingYear,
        @Min(1901) @Max(2155) Integer maximumPassingYear,
        @PositiveOrZero Integer maximumBacklogs) {

    @jakarta.validation.constraints.AssertTrue(message = "minimumPassingYear must not exceed maximumPassingYear")
    public boolean isPassingYearRangeValid() {
        return minimumPassingYear == null
                || maximumPassingYear == null
                || minimumPassingYear <= maximumPassingYear;
    }
}
