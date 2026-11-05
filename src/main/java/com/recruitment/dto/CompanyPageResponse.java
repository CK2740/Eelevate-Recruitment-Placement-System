package com.recruitment.dto;

import java.util.List;

public record CompanyPageResponse(
        List<CompanyResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
