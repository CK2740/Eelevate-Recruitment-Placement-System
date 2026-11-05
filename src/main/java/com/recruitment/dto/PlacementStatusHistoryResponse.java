package com.recruitment.dto;

import java.time.LocalDateTime;

public record PlacementStatusHistoryResponse(
        long historyId,
        PlacementStatus previousStatus,
        PlacementStatus newStatus,
        Long changedByUserId,
        String reason,
        LocalDateTime changedAt) {
}
