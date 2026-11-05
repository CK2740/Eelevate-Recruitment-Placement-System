package com.recruitment.dto;

import java.util.List;

public record JobPageResponse(
        List<JobResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
