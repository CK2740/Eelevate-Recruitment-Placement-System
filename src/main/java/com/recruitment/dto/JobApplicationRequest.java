package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record JobApplicationRequest(
        @NotNull @Positive Long candidateId) {
}
