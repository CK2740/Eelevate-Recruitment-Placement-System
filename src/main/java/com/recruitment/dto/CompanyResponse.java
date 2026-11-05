package com.recruitment.dto;

import java.time.LocalDateTime;

public record CompanyResponse(
        long companyId,
        String companyName,
        String industry,
        String location,
        String contactPerson,
        String email,
        String mobile,
        String website,
        CompanyStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
