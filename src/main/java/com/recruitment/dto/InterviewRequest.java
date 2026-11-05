package com.recruitment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;
import java.util.List;

public record InterviewRequest(
        @NotNull @Min(1) Long applicationId,
        @NotBlank @Size(max = 100) String interviewRound,
        @NotBlank @Pattern(regexp = "SCREENING|TECHNICAL|HR|MANAGERIAL|FINAL|OTHER")
        String interviewType,
        @NotNull @Min(1) @Max(20) Integer roundNumber,
        @NotNull @Future LocalDateTime scheduledAt,
        @NotNull @Min(15) @Max(480) Integer durationMinutes,
        @NotNull InterviewMode interviewMode,
        @Size(max = 255) String location,
        @Size(max = 1000) String meetingLink,
        @NotEmpty List<@Valid InterviewerRequest> interviewers) {

    @AssertTrue(message = "A location is required for in-person interviews and a meeting link for video interviews")
    public boolean isLocationValidForMode() {
        return switch (interviewMode == null ? InterviewMode.PHONE : interviewMode) {
            case IN_PERSON -> location != null && !location.isBlank();
            case VIDEO -> meetingLink != null && !meetingLink.isBlank();
            case PHONE -> true;
        };
    }
}
