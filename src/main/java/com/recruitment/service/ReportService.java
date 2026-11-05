package com.recruitment.service;

import com.recruitment.dao.ReportDao;
import com.recruitment.dto.ReportFilters;
import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class ReportService {

    /** Maximum allowed rows returned to the report page. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Number of rows retrieved for each CSV export query. */
    private static final int CSV_BATCH_SIZE = 500;

    private final ReportDao reportDao;

    public ReportService(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    public ReportResponse getReport(
            ReportType type,
            ReportFilters filters,
            final int page,
            final int size) {
        validateFilters(filters);
        validatePagination(page, size);
        long offset = (long) page * size;
        List<Map<String, Object>> rows = reportDao.getReportPage(
                type, filters, size, offset);
        long totalElements = reportDao.countReport(type, filters);
        long totalPages = totalElements / size
                + (totalElements % size == 0 ? 0 : 1);
        return new ReportResponse(
                type, rows, LocalDateTime.now(), page, size, totalElements,
                totalPages);
    }

    public StreamingResponseBody exportCsv(
            ReportType type,
            ReportFilters filters) {
        validateFilters(filters);
        return output -> writeCsv(output, type, filters);
    }

    private void writeCsv(
            final OutputStream output,
            final ReportType type,
            final ReportFilters filters) throws IOException {
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8));
        List<String> columns = columnsFor(type);
        appendRow(writer, columns);
        writer.flush();

        long offset = 0;
        while (true) {
            List<Map<String, Object>> rows = reportDao.getReportPage(
                    type,
                    filters,
                    CSV_BATCH_SIZE,
                    offset);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                appendRow(writer, columns.stream()
                        .map(row::get)
                        .map(ReportService::csvValue)
                        .toList());
            }
            writer.flush();
            offset += rows.size();
            if (rows.size() < CSV_BATCH_SIZE) {
                break;
            }
        }
    }

    private static List<String> columnsFor(ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL -> List.of("stage", "application_count");
            case COMPANY_WISE_PLACEMENTS ->
                    List.of("company", "total_placements", "joined_candidates");
            case TECHNOLOGY_WISE_PLACEMENTS ->
                    List.of("technology", "total_placements", "joined_candidates");
            case RECRUITER_PERFORMANCE -> List.of(
                    "recruiter_id",
                    "recruiter",
                    "jobs_created",
                    "applications",
                    "scheduled_interviews",
                    "selected_candidates",
                    "joined_candidates");
            case PENDING_INTERVIEWS -> List.of(
                    "interview_id",
                    "candidate",
                    "job_title",
                    "company",
                    "interview_round",
                    "scheduled_at",
                    "interview_mode");
            case OPEN_JOB_AGING -> List.of(
                    "job_id",
                    "job_title",
                    "company",
                    "technology",
                    "location",
                    "age_days",
                    "application_deadline");
        };
    }

    private static void validateFilters(
            ReportFilters filters) {
        if (filters.from() != null
                && filters.to() != null
                && filters.from().isAfter(filters.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        if ((filters.companyId() != null && filters.companyId() < 1)
                || (filters.recruiterId() != null && filters.recruiterId() < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive");
        }
        if (filters.passingYear() != null
                && (filters.passingYear() < 1901 || filters.passingYear() > 2155)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passingYear is out of range");
        }
    }

    private static void validatePagination(final int page, final int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination values");
        }
    }

    private static void appendRow(
            BufferedWriter writer,
            List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            String value = String.valueOf(values.get(i) == null ? "" : values.get(i));
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private static String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        String leadingTrimmed = text.stripLeading();
        if (value instanceof String
                && !leadingTrimmed.isEmpty()
                && "=+-@".indexOf(leadingTrimmed.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }
}
