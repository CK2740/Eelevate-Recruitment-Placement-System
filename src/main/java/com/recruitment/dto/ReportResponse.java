package com.recruitment.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReportResponse(
        ReportType reportType,
        List<Map<String, Object>> rows,
        LocalDateTime generatedAt,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    /**
     * Creates a response representing one complete result page.
     */
    public ReportResponse(
            final ReportType type,
            final List<Map<String, Object>> reportRows,
            final LocalDateTime timestamp) {
        this(
                type,
                reportRows,
                timestamp,
                0,
                reportRows.size(),
                reportRows.size(),
                reportRows.isEmpty() ? 0 : 1);
    }
}
