package com.recruitment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InterviewerFeedbackRequest(
        @DecimalMin("0.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1)
        BigDecimal technicalRating,
        @DecimalMin("0.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1)
        BigDecimal communicationRating,
        @NotNull InterviewRecommendation recommendation,
        @Size(max = 10000) String comments) {
}
