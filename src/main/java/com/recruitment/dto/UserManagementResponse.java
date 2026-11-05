package com.recruitment.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserManagementResponse(
        long userId,
        String email,
        String fullName,
        List<String> roles,
        boolean active,
        String accountStatus,
        LocalDateTime createdAt) {
}
