package com.recruitment.dto;

import java.util.List;

public record PlacementPageResponse(
        List<PlacementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
