package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;

public record RecruiterApprovalRequest(@NotNull Decision decision) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
