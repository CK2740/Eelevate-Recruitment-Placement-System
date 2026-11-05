package com.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CandidateResponse(
        long candidateId,
        String fullName,
        String email,
        String mobile,
        String qualification,
        int passingYear,
        BigDecimal percentage,
        int backlogCount,
        String skills,
        CandidateStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
