package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.ReportDao;
import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import com.recruitment.service.ReportService;
import java.sql.Date;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportDao reportDao;

    @InjectMocks
    private ReportService reportService;

    @Test
    void forwardsAllReportFiltersToDao() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(reportDao.getReport(
                ReportType.RECRUITMENT_FUNNEL,
                Date.valueOf(from),
                Date.valueOf(to),
                9L,
                "Java",
                4L,
                2025))
                .thenReturn(List.of(Map.of("stage", "SELECTED", "application_count", 3)));

        ReportResponse result = reportService.getReport(
                ReportType.RECRUITMENT_FUNNEL, from, to, 9L, "Java", 4L, 2025);

        assertEquals(ReportType.RECRUITMENT_FUNNEL, result.reportType());
        assertEquals(3, result.rows().get(0).get("application_count"));
        verify(reportDao).getReport(
                ReportType.RECRUITMENT_FUNNEL,
                Date.valueOf(from),
                Date.valueOf(to),
                9L,
                "Java",
                4L,
                2025);
    }

    @Test
    void exportsReportAsEscapedCsv() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("technology", "Java, \"Spring\"");
        row.put("total_placements", 3);
        row.put("joined_candidates", 2);
        when(reportDao.getReport(ReportType.TECHNOLOGY_WISE_PLACEMENTS, null, null, null, null, null, null))
                .thenReturn(List.of(row));

        String csv = new String(reportService.exportCsv(
                ReportType.TECHNOLOGY_WISE_PLACEMENTS, null, null, null, null, null, null),
                StandardCharsets.UTF_8);

        assertEquals("\"technology\",\"total_placements\",\"joined_candidates\"\r\n"
                + "\"Java, \"\"Spring\"\"\",\"3\",\"2\"\r\n", csv);
    }

    @Test
    void exportsHeadersForEmptyReportsAndNeutralizesSpreadsheetFormulas() {
        when(reportDao.getReport(ReportType.RECRUITMENT_FUNNEL, null, null, null, null, null, null))
                .thenReturn(List.of());
        String emptyCsv = new String(reportService.exportCsv(
                ReportType.RECRUITMENT_FUNNEL, null, null, null, null, null, null),
                StandardCharsets.UTF_8);
        assertEquals("\"stage\",\"application_count\"\r\n", emptyCsv);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", "=HYPERLINK(\"https://example.invalid\")");
        row.put("application_count", 1);
        when(reportDao.getReport(ReportType.RECRUITMENT_FUNNEL, null, null, null, null, null, null))
                .thenReturn(List.of(row));
        String csv = new String(reportService.exportCsv(
                ReportType.RECRUITMENT_FUNNEL, null, null, null, null, null, null),
                StandardCharsets.UTF_8);
        assertEquals("\"stage\",\"application_count\"\r\n"
                + "\"'=HYPERLINK(\"\"https://example.invalid\"\")\",\"1\"\r\n", csv);
    }

    @Test
    void exportsStableHeadersForEveryReportType() {
        Map<ReportType, String> expectedHeaders = Map.of(
                ReportType.RECRUITMENT_FUNNEL, "\"stage\",\"application_count\"\r\n",
                ReportType.COMPANY_WISE_PLACEMENTS,
                        "\"company\",\"total_placements\",\"joined_candidates\"\r\n",
                ReportType.TECHNOLOGY_WISE_PLACEMENTS,
                        "\"technology\",\"total_placements\",\"joined_candidates\"\r\n",
                ReportType.RECRUITER_PERFORMANCE,
                        "\"recruiter_id\",\"recruiter\",\"jobs_created\",\"applications\","
                                + "\"scheduled_interviews\",\"selected_candidates\",\"joined_candidates\"\r\n",
                ReportType.PENDING_INTERVIEWS,
                        "\"interview_id\",\"candidate\",\"job_title\",\"company\","
                                + "\"interview_round\",\"scheduled_at\",\"interview_mode\"\r\n",
                ReportType.OPEN_JOB_AGING,
                        "\"job_id\",\"job_title\",\"company\",\"technology\",\"location\","
                                + "\"age_days\",\"application_deadline\"\r\n");
        for (ReportType type : ReportType.values()) {
            when(reportDao.getReport(type, null, null, null, null, null, null)).thenReturn(List.of());
        }

        for (ReportType type : ReportType.values()) {
            String csv = new String(reportService.exportCsv(
                    type, null, null, null, null, null, null), StandardCharsets.UTF_8);
            assertEquals(expectedHeaders.get(type), csv, type.name());
        }
    }

    @Test
    void rejectsInvalidDateRangeWithoutQueryingDao() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reportService.getReport(
                        ReportType.OPEN_JOB_AGING,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1),
                        null,
                        null,
                        null,
                        null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
