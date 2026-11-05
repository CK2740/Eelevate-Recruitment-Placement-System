package com.recruitment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InterviewerFeedbackSubmission(
        @NotNull @Positive Long interviewerId,
        @NotNull @Valid InterviewerFeedbackRequest feedback) {
}
