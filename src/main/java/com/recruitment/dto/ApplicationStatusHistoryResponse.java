package com.recruitment.dto;

import java.time.LocalDateTime;

public record ApplicationStatusHistoryResponse(
        long historyId,
        ApplicationStatus previousStatus,
        ApplicationStatus newStatus,
        Long changedByUserId,
        String reason,
        LocalDateTime changedAt) {
}
