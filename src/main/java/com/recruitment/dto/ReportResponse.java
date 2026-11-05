package com.recruitment.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReportResponse(
        ReportType reportType,
        List<Map<String, Object>> rows,
        LocalDateTime generatedAt) {
}
