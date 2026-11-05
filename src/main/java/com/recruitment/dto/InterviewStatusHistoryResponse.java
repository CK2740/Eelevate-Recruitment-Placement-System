package com.recruitment.dto;

import java.time.LocalDateTime;

public record InterviewStatusHistoryResponse(
        long historyId,
        InterviewStatus previousStatus,
        InterviewStatus newStatus,
        Long changedByUserId,
        String reason,
        LocalDateTime changedAt) {
}
