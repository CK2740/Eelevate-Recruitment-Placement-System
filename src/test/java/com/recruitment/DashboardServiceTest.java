package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.DashboardDao;
import com.recruitment.dto.DashboardMetricsResponse;
import com.recruitment.service.DashboardService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardDao dashboardDao;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void returnsAggregatedDashboardMetrics() {
        DashboardMetricsResponse expected = new DashboardMetricsResponse(
                100, 12, 8, 40, 6, 9, 5, new BigDecimal("5.00"));
        when(dashboardDao.getMetrics()).thenReturn(expected);

        assertEquals(expected, dashboardService.getMetrics());
        verify(dashboardDao).getMetrics();
    }
}
