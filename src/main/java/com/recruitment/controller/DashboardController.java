package com.recruitment.controller;

import com.recruitment.dto.DashboardMetricsResponse;
import com.recruitment.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public DashboardMetricsResponse getMetrics() {
        return dashboardService.getMetrics();
    }
}
