package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InterviewStatusRequest(
        @NotNull InterviewStatus status,
        @Size(max = 500) String reason) {
}
