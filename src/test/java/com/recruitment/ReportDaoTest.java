package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.ReportDao;
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

        reportDao.getReport(
                ReportType.RECRUITMENT_FUNNEL,
                Date.valueOf(from),
                Date.valueOf(to),
                14L,
                "Java",
                9L,
                2025);

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
                2025
        }, parameters.getValue());
    }

    @Test
    void appliesPassingYearFilterToOpenJobApplications() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        reportDao.getReport(ReportType.OPEN_JOB_AGING, null, null, null, null, null, 2024);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("year_candidate.passing_year = ?"));
        assertArrayEquals(new Object[] {2024}, parameters.getValue());
    }
}
