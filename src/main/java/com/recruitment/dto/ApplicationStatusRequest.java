package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationStatusRequest(
        @NotNull ApplicationStatus status,
        @Size(max = 500) String reason) {
}
