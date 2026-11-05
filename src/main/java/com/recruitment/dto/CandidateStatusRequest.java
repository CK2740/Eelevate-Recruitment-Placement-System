package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;

public record CandidateStatusRequest(@NotNull CandidateStatus status) {
}
