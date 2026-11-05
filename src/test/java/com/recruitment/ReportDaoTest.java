package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.ReportDao;
import com.recruitment.dto.ReportFilters;
import com.recruitment.dto.ReportType;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ReportDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportDao reportDao;

    @Test
    void appliesDateCompanyTechnologyRecruiterAndPassingYearFiltersAsParameters() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        reportDao.getReportPage(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(from, to, 14L, "Java", 9L, 2025),
                100,
                0);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("a.applied_at >= ?"));
        assertTrue(sql.getValue().contains("j.company_id = ?"));
        assertTrue(sql.getValue().contains("j.technology LIKE ?"));
        assertTrue(sql.getValue().contains("j.created_by_user_id = ?"));
        assertTrue(sql.getValue().contains("c.passing_year = ?"));
        assertArrayEquals(new Object[] {
                Date.valueOf(from),
                Date.valueOf(to),
                14L,
                "%Java%",
                9L,
                2025,
                100,
                0L
        }, parameters.getValue());
    }

    @Test
    void appliesPassingYearFilterToOpenJobApplications() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        reportDao.getReportPage(
                ReportType.OPEN_JOB_AGING,
                new ReportFilters(null, null, null, null, null, 2024),
                100,
                0);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("year_candidate.passing_year = ?"));
        assertArrayEquals(new Object[] {2024, 100, 0L}, parameters.getValue());
    }

    @Test
    void buildsExpectedSqlAndFiltersForEveryReportType() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        List<ReportType> types = List.of(ReportType.values());
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        for (ReportType type : types) {
            reportDao.getReportPage(
                    type,
                    new ReportFilters(from, to, 14L, "Java", 9L, 2025),
                    100,
                    0);
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(types.size())).queryForList(sql.capture(), parameters.capture());
        List<String> queries = sql.getAllValues();
        List<Object[]> queryParameters = parameters.getAllValues();
        assertEquals(types.size(), queries.size());

        for (int index = 0; index < types.size(); index++) {
            String query = queries.get(index);
            assertTrue(query.contains("j.company_id = ?"), types.get(index).name());
            assertTrue(query.contains("j.technology LIKE ?"), types.get(index).name());
            assertTrue(query.contains("j.created_by_user_id = ?"), types.get(index).name());
            assertTrue(query.contains("c.passing_year = ?") || query.contains("year_candidate.passing_year = ?"),
                    types.get(index).name());
            assertArrayEquals(new Object[] {
                    Date.valueOf(from), Date.valueOf(to), 14L, "%Java%", 9L, 2025, 100, 0L
            }, queryParameters.get(index));
        }

        String funnel = queries.get(types.indexOf(ReportType.RECRUITMENT_FUNNEL));
        assertTrue(funnel.contains("a.status AS stage"));
        assertTrue(funnel.contains("GROUP BY a.status"));

        for (ReportType placementType : List.of(
                ReportType.COMPANY_WISE_PLACEMENTS,
                ReportType.TECHNOLOGY_WISE_PLACEMENTS)) {
            String query = queries.get(types.indexOf(placementType));
            assertTrue(query.contains("LEFT JOIN placements p ON p.application_id = a.application_id"));
            assertTrue(query.contains("(p.placement_id IS NOT NULL OR a.status = 'JOINED')"));
            assertTrue(query.contains("p.joining_status = 'JOINED'"));
            assertTrue(query.contains("a.status = 'JOINED'"));
            assertTrue(query.contains("COALESCE(p.actual_joining_date, p.created_at, a.updated_at)"));
        }

        String recruiterPerformance = queries.get(types.indexOf(ReportType.RECRUITER_PERFORMANCE));
        assertTrue(recruiterPerformance.contains("r.role_code = 'RECRUITER'"));
        assertTrue(recruiterPerformance.contains("a.status = 'JOINED'"));
        assertTrue(queries.get(types.indexOf(ReportType.PENDING_INTERVIEWS))
                .contains("i.status = 'SCHEDULED'"));
        assertTrue(queries.get(types.indexOf(ReportType.OPEN_JOB_AGING))
                .contains("j.status = 'OPEN'"));
    }

    @Test
    void countsGroupedRowsWithoutApplyingPagination() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                any(Object[].class))).thenReturn(25L);

        long count = reportDao.countReport(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(null, null, null, null, null, null));

        assertEquals(25L, count);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sql.capture(),
                org.mockito.ArgumentMatchers.eq(Long.class),
                any(Object[].class));
        assertTrue(sql.getValue().startsWith("SELECT COUNT(*) FROM (SELECT "));
        assertTrue(sql.getValue().contains("GROUP BY a.status) report_rows"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.getValue().contains("LIMIT"));
    }
}
