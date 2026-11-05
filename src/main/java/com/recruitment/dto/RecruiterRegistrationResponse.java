package com.recruitment.dto;

import java.time.LocalDateTime;

public record RecruiterRegistrationResponse(
        long userId,
        String email,
        String fullName,
        LocalDateTime registeredAt) {
}
