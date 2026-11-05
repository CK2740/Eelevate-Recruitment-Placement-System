package com.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlacementResponse(
        long placementId,
        long applicationId,
        long candidateId,
        String candidateName,
        long companyId,
        String companyName,
        String designation,
        BigDecimal finalPackage,
        LocalDate actualJoiningDate,
        PlacementStatus joiningStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
