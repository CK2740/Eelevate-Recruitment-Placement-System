package com.recruitment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record InterviewRescheduleRequest(
        @NotNull @Future LocalDateTime scheduledAt,
        @Min(15) @Max(480) Integer durationMinutes,
        @Size(max = 255) String location,
        @Size(max = 1000) String meetingLink) {
}
