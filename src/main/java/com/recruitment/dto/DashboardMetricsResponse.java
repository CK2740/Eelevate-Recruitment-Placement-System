package com.recruitment.dto;

import java.math.BigDecimal;

public record DashboardMetricsResponse(
        long totalCandidates,
        long totalCompanies,
        long openJobs,
        long totalApplications,
        long scheduledInterviews,
        long selectedCandidates,
        long joinedCandidates,
        BigDecimal placementPercentage) {
}
