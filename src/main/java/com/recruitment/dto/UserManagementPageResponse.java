package com.recruitment.dto;

import java.util.List;

public record UserManagementPageResponse(
        List<UserManagementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
