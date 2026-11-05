package com.recruitment.dto;

import java.util.List;

public record InterviewPageResponse(
        List<InterviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
