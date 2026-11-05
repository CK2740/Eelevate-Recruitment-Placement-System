package com.recruitment.service;

import com.recruitment.dao.ReportDao;
import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    private final ReportDao reportDao;

    public ReportService(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    public ReportResponse getReport(
            ReportType type,
            LocalDate from,
            LocalDate to,
            Long companyId,
            String technology,
            Long recruiterId,
            Integer passingYear) {
        validateFilters(from, to, companyId, recruiterId, passingYear);
        List<Map<String, Object>> rows = reportDao.getReport(
                type,
                from == null ? null : Date.valueOf(from),
                to == null ? null : Date.valueOf(to),
                companyId,
                technology,
                recruiterId,
                passingYear);
        return new ReportResponse(type, rows, LocalDateTime.now());
    }

    public byte[] exportCsv(
            ReportType type,
            LocalDate from,
            LocalDate to,
            Long companyId,
            String technology,
            Long recruiterId,
            Integer passingYear) {
        List<Map<String, Object>> rows = getReport(
                type, from, to, companyId, technology, recruiterId, passingYear).rows();
        StringBuilder csv = new StringBuilder();
        List<String> columns = columnsFor(type);
        appendRow(csv, columns);
        for (Map<String, Object> row : rows) {
            appendRow(csv, columns.stream().map(row::get).map(ReportService::csvValue).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
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
            LocalDate from,
            LocalDate to,
            Long companyId,
            Long recruiterId,
            Integer passingYear) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        if (companyId != null && companyId < 1 || recruiterId != null && recruiterId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive");
        }
        if (passingYear != null && (passingYear < 1901 || passingYear > 2155)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passingYear is out of range");
        }
    }

    private static void appendRow(StringBuilder csv, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = String.valueOf(values.get(i) == null ? "" : values.get(i));
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
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
