package com.recruitment.dto;

import java.time.LocalDateTime;

public record OfferStatusHistoryResponse(
        long historyId,
        OfferStatus previousStatus,
        OfferStatus newStatus,
        Long changedByUserId,
        String reason,
        LocalDateTime changedAt) {
}
