package com.recruitment.dto;

import java.util.List;

public record CandidatePageResponse(
        List<CandidateResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
