package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.ReportDao;
import com.recruitment.dto.ReportFilters;
import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import com.recruitment.service.ReportService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportDao reportDao;

    @InjectMocks
    private ReportService reportService;

    @Test
    void forwardsFiltersAndReturnsAccuratePageMetadata() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(reportDao.getReportPage(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(from, to, 9L, "Java", 4L, 2025),
                25,
                25L))
                .thenReturn(List.of(Map.of("stage", "SELECTED", "application_count", 3)));
        when(reportDao.countReport(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(from, to, 9L, "Java", 4L, 2025)))
                .thenReturn(51L);

        ReportResponse result = reportService.getReport(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(from, to, 9L, "Java", 4L, 2025),
                1,
                25);

        assertEquals(ReportType.RECRUITMENT_FUNNEL, result.reportType());
        assertEquals(3, result.rows().get(0).get("application_count"));
        assertEquals(1, result.page());
        assertEquals(25, result.size());
        assertEquals(51, result.totalElements());
        assertEquals(3, result.totalPages());
        verify(reportDao).getReportPage(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(from, to, 9L, "Java", 4L, 2025),
                25,
                25L);
    }

    @Test
    void exportsCsvWithEscapingAndPreservesColumnOrder() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("technology", "Java, \"Spring\"");
        row.put("total_placements", 3);
        row.put("joined_candidates", 2);
        when(reportDao.getReportPage(
                ReportType.TECHNOLOGY_WISE_PLACEMENTS,
                new ReportFilters(null, null, null, null, null, null),
                500,
                0L))
                .thenReturn(List.of(row));

        String csv = export(ReportType.TECHNOLOGY_WISE_PLACEMENTS);

        assertEquals("\"technology\",\"total_placements\",\"joined_candidates\"\r\n"
                + "\"Java, \"\"Spring\"\"\",\"3\",\"2\"\r\n", csv);
    }

    @Test
    void exportsHeadersForEmptyReportsAndNeutralizesSpreadsheetFormulas() throws Exception {
        when(reportDao.getReportPage(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(null, null, null, null, null, null),
                500,
                0L))
                .thenReturn(List.of());
        String emptyCsv = export(ReportType.RECRUITMENT_FUNNEL);
        assertEquals("\"stage\",\"application_count\"\r\n", emptyCsv);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", "=HYPERLINK(\"https://example.invalid\")");
        row.put("application_count", 1);
        when(reportDao.getReportPage(
                ReportType.RECRUITMENT_FUNNEL,
                new ReportFilters(null, null, null, null, null, null),
                500,
                0L))
                .thenReturn(List.of(row));
        String csv = export(ReportType.RECRUITMENT_FUNNEL);
        assertEquals("\"stage\",\"application_count\"\r\n"
                + "\"'=HYPERLINK(\"\"https://example.invalid\"\")\",\"1\"\r\n", csv);
    }

    @Test
    void exportsStableHeadersForEveryReportType() throws Exception {
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
            when(reportDao.getReportPage(
                    type,
                    new ReportFilters(null, null, null, null, null, null),
                    500,
                    0L))
                    .thenReturn(List.of());
        }

        for (ReportType type : ReportType.values()) {
            assertEquals(expectedHeaders.get(type), export(type), type.name());
        }
    }

    @Test
    void streamsLargeCsvUsingBoundedDatabasePages() throws Exception {
        List<Map<String, Object>> firstPage = funnelRows(500, 0);
        List<Map<String, Object>> secondPage = funnelRows(500, 500);
        List<Map<String, Object>> lastPage = funnelRows(200, 1000);
        when(reportDao.getReportPage(
                eq(ReportType.RECRUITMENT_FUNNEL),
                eq(new ReportFilters(null, null, null, null, null, null)),
                eq(500),
                eq(0L)))
                .thenReturn(firstPage);
        when(reportDao.getReportPage(
                eq(ReportType.RECRUITMENT_FUNNEL),
                eq(new ReportFilters(null, null, null, null, null, null)),
                eq(500),
                eq(500L)))
                .thenReturn(secondPage);
        when(reportDao.getReportPage(
                eq(ReportType.RECRUITMENT_FUNNEL),
                eq(new ReportFilters(null, null, null, null, null, null)),
                eq(500),
                eq(1000L)))
                .thenReturn(lastPage);

        String csv = export(ReportType.RECRUITMENT_FUNNEL);

        assertEquals(1201, csv.lines().count());
        verify(reportDao, times(3)).getReportPage(
                eq(ReportType.RECRUITMENT_FUNNEL),
                eq(new ReportFilters(null, null, null, null, null, null)),
                eq(500),
                anyLong());
    }

    @Test
    void rejectsInvalidDateRangeAndPaginationWithoutQueryingDao() {
        ResponseStatusException invalidDates = assertThrows(
                ResponseStatusException.class,
                () -> reportService.getReport(
                        ReportType.OPEN_JOB_AGING,
                        new ReportFilters(
                                LocalDate.of(2026, 2, 1),
                                LocalDate.of(2026, 1, 1),
                                null,
                                null,
                                null,
                                null),
                        0,
                        100));
        ResponseStatusException invalidPage = assertThrows(
                ResponseStatusException.class,
                () -> reportService.getReport(
                        ReportType.OPEN_JOB_AGING,
                        new ReportFilters(null, null, null, null, null, null),
                        0,
                        101));

        assertEquals(HttpStatus.BAD_REQUEST, invalidDates.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidPage.getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(reportDao);
    }

    private String export(ReportType type) throws Exception {
        StreamingResponseBody body = reportService.exportCsv(
                type, new ReportFilters(null, null, null, null, null, null));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static List<Map<String, Object>> funnelRows(int count, int start) {
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(Map.of("stage", "APPLIED", "application_count", start + index));
        }
        return rows;
    }
}
