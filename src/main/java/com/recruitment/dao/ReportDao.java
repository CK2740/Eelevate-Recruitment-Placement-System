package com.recruitment.dao;

import com.recruitment.dto.ReportType;
import com.recruitment.dto.ReportFilters;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportDao {

    private final JdbcTemplate jdbcTemplate;

    public ReportDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves a bounded page of rows while preserving the report's filters and ordering.
     *
     * @param type selected report
     * @param from inclusive start date
     * @param to inclusive end date
     * @param companyId optional company filter
     * @param technology optional technology filter
     * @param recruiterId optional recruiter filter
     * @param passingYear optional candidate passing-year filter
     * @param limit maximum rows to retrieve
     * @param offset number of rows to skip
     * @return the requested report page
     */
    public List<Map<String, Object>> getReportPage(
            final ReportType type,
            final ReportFilters filters,
            final int limit,
            final long offset) {
        ReportQuery query = buildQuery(type, filters, true);
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(limit);
        parameters.add(offset);
        return jdbcTemplate.queryForList(
                query.sql() + " LIMIT ? OFFSET ?", parameters.toArray());
    }

    /**
     * Counts report rows using the same filters as the paginated report query.
     *
     * @param type selected report
     * @param from inclusive start date
     * @param to inclusive end date
     * @param companyId optional company filter
     * @param technology optional technology filter
     * @param recruiterId optional recruiter filter
     * @param passingYear optional candidate passing-year filter
     * @return total matching report rows
     */
    public long countReport(
            final ReportType type,
            final ReportFilters filters) {
        ReportQuery query = buildQuery(type, filters, false);
        String sql = "SELECT COUNT(*) FROM (" + query.sql() + ") report_rows";
        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                query.parameters().toArray());
        return count == null ? 0 : count;
    }

    private static ReportQuery buildQuery(
            final ReportType type,
            final ReportFilters filters,
            final boolean ordered) {
        String dateColumn = switch (type) {
            case RECRUITMENT_FUNNEL -> "a.applied_at";
            case COMPANY_WISE_PLACEMENTS, TECHNOLOGY_WISE_PLACEMENTS ->
                    "COALESCE(p.actual_joining_date, p.created_at, a.updated_at)";
            case RECRUITER_PERFORMANCE -> "a.applied_at";
            case OPEN_JOB_AGING -> "j.created_at";
            case PENDING_INTERVIEWS -> "i.scheduled_at";
        };
        String sql = "SELECT " + selectClause(type) + " FROM " + fromClause(type)
                + " WHERE " + baseConditions(type);
        List<Object> parameters = new ArrayList<>();
        if (type == ReportType.PENDING_INTERVIEWS) {
            sql += " AND i.status = 'SCHEDULED'";
        }
        if (type == ReportType.OPEN_JOB_AGING) {
            sql += " AND j.status = 'OPEN'";
        }
        if (type == ReportType.COMPANY_WISE_PLACEMENTS
                || type == ReportType.TECHNOLOGY_WISE_PLACEMENTS) {
            sql += " AND (p.placement_id IS NOT NULL OR a.status = 'JOINED')";
        }
        if (filters.from() != null) {
            sql += " AND " + dateColumn + " >= ?";
            parameters.add(Date.valueOf(filters.from()));
        }
        if (filters.to() != null) {
            sql += " AND " + dateColumn + " < DATE_ADD(?, INTERVAL 1 DAY)";
            parameters.add(Date.valueOf(filters.to()));
        }
        if (filters.companyId() != null) {
            sql += " AND j.company_id = ?";
            parameters.add(filters.companyId());
        }
        if (filters.technology() != null && !filters.technology().isBlank()) {
            sql += " AND j.technology LIKE ?";
            parameters.add("%" + filters.technology().trim() + "%");
        }
        if (filters.recruiterId() != null) {
            sql += " AND j.created_by_user_id = ?";
            parameters.add(filters.recruiterId());
        }
        if (filters.passingYear() != null) {
            if (type == ReportType.OPEN_JOB_AGING) {
                sql += """
                         AND EXISTS (
                             SELECT 1
                             FROM applications year_application
                             JOIN candidates year_candidate
                               ON year_candidate.candidate_id = year_application.candidate_id
                             WHERE year_application.job_id = j.job_id
                               AND year_candidate.passing_year = ?
                         )
                        """;
            } else {
                sql += " AND c.passing_year = ?";
            }
            parameters.add(filters.passingYear());
        }
        sql += groupBy(type);
        if (ordered) {
            sql += orderBy(type);
        }
        return new ReportQuery(sql, parameters);
    }

    private static String selectClause(ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL ->
                    "a.status AS stage, COUNT(*) AS application_count";
            case COMPANY_WISE_PLACEMENTS ->
                    "co.company_name AS company, "
                            + "(COUNT(DISTINCT p.placement_id) + "
                            + "COUNT(DISTINCT CASE WHEN p.placement_id IS NULL AND a.status = 'JOINED' "
                            + "THEN a.application_id END)) AS total_placements, "
                            + "(COUNT(DISTINCT CASE WHEN p.joining_status = 'JOINED' OR a.status = 'JOINED' "
                            + "THEN p.placement_id END) + "
                            + "COUNT(DISTINCT CASE WHEN p.placement_id IS NULL AND a.status = 'JOINED' "
                            + "THEN a.application_id END)) AS joined_candidates";
            case TECHNOLOGY_WISE_PLACEMENTS ->
                    "j.technology AS technology, "
                            + "(COUNT(DISTINCT p.placement_id) + "
                            + "COUNT(DISTINCT CASE WHEN p.placement_id IS NULL AND a.status = 'JOINED' "
                            + "THEN a.application_id END)) AS total_placements, "
                            + "(COUNT(DISTINCT CASE WHEN p.joining_status = 'JOINED' OR a.status = 'JOINED' "
                            + "THEN p.placement_id END) + "
                            + "COUNT(DISTINCT CASE WHEN p.placement_id IS NULL AND a.status = 'JOINED' "
                            + "THEN a.application_id END)) AS joined_candidates";
            case RECRUITER_PERFORMANCE ->
                    "u.user_id AS recruiter_id, u.full_name AS recruiter, "
                            + "COUNT(DISTINCT j.job_id) AS jobs_created, "
                            + "COUNT(DISTINCT a.application_id) AS applications, "
                            + "COUNT(DISTINCT CASE WHEN i.status = 'SCHEDULED' "
                            + "THEN i.interview_id END) AS scheduled_interviews, "
                            + "COUNT(DISTINCT CASE WHEN a.status = 'SELECTED' "
                            + "THEN a.application_id END) AS selected_candidates, "
                            + "(COUNT(DISTINCT CASE WHEN p.joining_status = 'JOINED' OR a.status = 'JOINED' "
                            + "THEN p.placement_id END) + "
                            + "COUNT(DISTINCT CASE WHEN p.placement_id IS NULL AND a.status = 'JOINED' "
                            + "THEN a.application_id END)) AS joined_candidates";
            case PENDING_INTERVIEWS ->
                    "i.interview_id, c.full_name AS candidate, j.job_title, "
                            + "co.company_name AS company, i.interview_round, "
                            + "i.scheduled_at, i.interview_mode";
            case OPEN_JOB_AGING ->
                    "j.job_id, j.job_title, co.company_name AS company, j.technology, "
                            + "j.location, DATEDIFF(CURRENT_DATE, DATE(j.created_at)) AS age_days, "
                            + "j.application_deadline";
        };
    }

    private static String fromClause(ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL ->
                    "applications a JOIN candidates c ON c.candidate_id = a.candidate_id "
                            + "JOIN jobs j ON j.job_id = a.job_id "
                            + "JOIN companies co ON co.company_id = j.company_id";
            case COMPANY_WISE_PLACEMENTS, TECHNOLOGY_WISE_PLACEMENTS ->
                    "applications a JOIN candidates c ON c.candidate_id = a.candidate_id "
                            + "JOIN jobs j ON j.job_id = a.job_id "
                            + "JOIN companies co ON co.company_id = j.company_id "
                            + "LEFT JOIN placements p ON p.application_id = a.application_id";
            case RECRUITER_PERFORMANCE ->
                    "users u JOIN user_roles ur ON ur.user_id = u.user_id "
                            + "JOIN roles r ON r.role_id = ur.role_id AND r.role_code = 'RECRUITER' "
                            + "JOIN jobs j ON j.created_by_user_id = u.user_id "
                            + "LEFT JOIN applications a ON a.job_id = j.job_id "
                            + "LEFT JOIN candidates c ON c.candidate_id = a.candidate_id "
                            + "LEFT JOIN interviews i ON i.application_id = a.application_id "
                            + "LEFT JOIN placements p ON p.application_id = a.application_id";
            case PENDING_INTERVIEWS ->
                    "interviews i JOIN applications a ON a.application_id = i.application_id "
                            + "JOIN candidates c ON c.candidate_id = a.candidate_id "
                            + "JOIN jobs j ON j.job_id = a.job_id "
                            + "JOIN companies co ON co.company_id = j.company_id";
            case OPEN_JOB_AGING ->
                    "jobs j JOIN companies co ON co.company_id = j.company_id";
        };
    }

    private static String baseConditions(ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL, COMPANY_WISE_PLACEMENTS, TECHNOLOGY_WISE_PLACEMENTS,
                    RECRUITER_PERFORMANCE, PENDING_INTERVIEWS, OPEN_JOB_AGING -> "1 = 1";
        };
    }

    private static String groupBy(final ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL -> " GROUP BY a.status";
            case COMPANY_WISE_PLACEMENTS -> " GROUP BY co.company_id, co.company_name";
            case TECHNOLOGY_WISE_PLACEMENTS -> " GROUP BY j.technology";
            case RECRUITER_PERFORMANCE -> " GROUP BY u.user_id, u.full_name";
            case PENDING_INTERVIEWS, OPEN_JOB_AGING -> "";
        };
    }

    private static String orderBy(final ReportType type) {
        return switch (type) {
            case RECRUITMENT_FUNNEL -> " ORDER BY a.status";
            case COMPANY_WISE_PLACEMENTS ->
                    " ORDER BY co.company_name, co.company_id";
            case TECHNOLOGY_WISE_PLACEMENTS -> " ORDER BY j.technology";
            case RECRUITER_PERFORMANCE ->
                    " ORDER BY u.full_name, u.user_id";
            case PENDING_INTERVIEWS -> " ORDER BY i.scheduled_at, i.interview_id";
            case OPEN_JOB_AGING -> " ORDER BY age_days DESC, j.job_id";
        };
    }

    private record ReportQuery(String sql, List<Object> parameters) {
    }
}
