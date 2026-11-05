package com.recruitment.dto;

import java.time.LocalDateTime;

public record ApplicationResponse(
        long applicationId,
        long candidateId,
        String candidateName,
        long jobId,
        String jobTitle,
        long companyId,
        String companyName,
        ApplicationStatus status,
        String statusReason,
        LocalDateTime appliedAt,
        LocalDateTime updatedAt) {
}
