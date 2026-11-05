package com.recruitment.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        long auditLogId,
        Long userId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String details,
        LocalDateTime createdAt) {
}
