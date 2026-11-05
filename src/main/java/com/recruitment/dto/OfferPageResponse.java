package com.recruitment.dto;

import java.util.List;

public record OfferPageResponse(
        List<OfferResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
