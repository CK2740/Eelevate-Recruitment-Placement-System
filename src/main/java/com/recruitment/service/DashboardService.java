package com.recruitment.service;

import com.recruitment.dao.DashboardDao;
import com.recruitment.dto.DashboardMetricsResponse;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final DashboardDao dashboardDao;

    public DashboardService(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
    }

    public DashboardMetricsResponse getMetrics() {
        return dashboardDao.getMetrics();
    }
}
