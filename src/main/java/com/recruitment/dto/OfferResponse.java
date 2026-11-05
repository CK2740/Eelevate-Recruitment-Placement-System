package com.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OfferResponse(
        long offerId,
        long applicationId,
        long candidateId,
        String candidateName,
        long companyId,
        String companyName,
        String designation,
        BigDecimal packageAmount,
        LocalDate offerDate,
        LocalDate expiryDate,
        LocalDate joiningDate,
        String offerDetails,
        OfferStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
