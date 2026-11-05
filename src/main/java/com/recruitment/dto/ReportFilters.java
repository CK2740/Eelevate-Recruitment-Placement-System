package com.recruitment.dto;

import java.time.LocalDate;

public record ReportFilters(
        LocalDate from,
        LocalDate to,
        Long companyId,
        String technology,
        Long recruiterId,
        Integer passingYear) {
}
