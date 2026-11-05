package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.JobDao;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobEligibilityRequest;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JobDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JobDao jobDao;

    @Test
    void candidateBrowseIncludesOpenJobsBeforeTheirDeadlineWithoutAnImplicitEligibilityFilter() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        doReturn(List.of(job(8))).when(jdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<JobResponse>>any(),
                any(Object[].class));

        JobPageResponse page = jobDao.findAvailable(
                null, null, null, null, null, null, null, 0, 20);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(countSql.getValue().contains("j.status = 'OPEN'"));
        assertTrue(countSql.getValue().contains("j.application_deadline >= CURRENT_DATE()"));
        assertFalse(countSql.getValue().contains("filter_ec"));

        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                pageSql.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<JobResponse>>any(),
                any(Object[].class));
        assertTrue(pageSql.getValue().contains("j.status = 'OPEN'"));
        assertTrue(pageSql.getValue().contains("j.application_deadline >= CURRENT_DATE()"));
        org.junit.jupiter.api.Assertions.assertEquals(JobStatus.OPEN, page.content().getFirst().status());
        org.junit.jupiter.api.Assertions.assertEquals(1, page.totalElements());
    }

    @Test
    void candidatePassingYearFilterOnlyExcludesJobsOutsideTheirConfiguredRange() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<JobResponse>>any(),
                any(Object[].class));

        jobDao.findAvailable(null, null, null, null, null, null, 2025, 0, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("filter_ec.minimum_passing_year <= ?"));
        assertTrue(sql.getValue().contains("filter_ec.maximum_passing_year >= ?"));
    }

    private static JobResponse job(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new JobResponse(
                id,
                3,
                "Acme Inc",
                "Software Engineer",
                "Java",
                4,
                "London",
                new BigDecimal("85000.00"),
                "Build backend systems",
                "Java, SQL",
                "Computer Science",
                LocalDate.now().plusDays(30),
                EmploymentType.FULL_TIME,
                JobStatus.OPEN,
                new JobEligibilityRequest(new BigDecimal("70.00"), 2023, 2026, 1),
                timestamp,
                timestamp);
    }

}
