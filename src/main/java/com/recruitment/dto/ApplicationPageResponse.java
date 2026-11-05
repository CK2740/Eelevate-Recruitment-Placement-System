package com.recruitment.dto;

import java.util.List;

public record ApplicationPageResponse(
        List<ApplicationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
