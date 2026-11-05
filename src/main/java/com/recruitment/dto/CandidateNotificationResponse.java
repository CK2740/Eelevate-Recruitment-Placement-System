package com.recruitment.dto;

import java.time.LocalDateTime;

public record CandidateNotificationResponse(
        long eventId,
        String type,
        String message,
        LocalDateTime occurredAt,
        long referenceId) {
}
