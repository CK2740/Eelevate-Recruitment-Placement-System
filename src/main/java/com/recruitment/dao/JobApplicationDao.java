package com.recruitment.dao;

import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JobApplicationDao {

    private static final String APPLICATION_COLUMNS = """
            a.application_id, a.candidate_id, c.full_name AS candidate_name, a.job_id,
            j.job_title, co.company_id, co.company_name, a.status, a.applied_at, a.updated_at,
            (SELECT h.change_reason FROM application_status_history h
             WHERE h.application_id = a.application_id
             ORDER BY h.changed_at DESC, h.history_id DESC LIMIT 1) AS status_reason
            """;

    private static final String APPLICATION_FROM = """
             FROM applications a
             JOIN candidates c ON c.candidate_id = a.candidate_id
             JOIN jobs j ON j.job_id = a.job_id
             JOIN companies co ON co.company_id = j.company_id
            """;

    private static final RowMapper<ApplicationResponse> APPLICATION_ROW_MAPPER =
            (resultSet, rowNumber) -> new ApplicationResponse(
                    resultSet.getLong("application_id"),
                    resultSet.getLong("candidate_id"),
                    resultSet.getString("candidate_name"),
                    resultSet.getLong("job_id"),
                    resultSet.getString("job_title"),
                    resultSet.getLong("company_id"),
                    resultSet.getString("company_name"),
                    ApplicationStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("status_reason"),
                    timestampToLocalDateTime(resultSet.getTimestamp("applied_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public JobApplicationDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApplicationEligibilityContext findEligibilityContext(long jobId, long candidateId) {
        return jdbcTemplate.queryForObject("""
                SELECT c.candidate_id, c.is_active, c.qualification, c.passing_year,
                       c.percentage, c.backlog_count, c.skills,
                       j.job_id, j.company_id, j.created_by_user_id, j.status,
                       j.application_deadline, j.required_qualification, j.skills AS required_skills,
                       COALESCE(ec.minimum_percentage, 0) AS minimum_percentage,
                       ec.minimum_passing_year, ec.maximum_passing_year,
                       COALESCE(ec.maximum_backlogs, 0) AS maximum_backlogs
                FROM jobs j
                JOIN candidates c ON c.candidate_id = ?
                LEFT JOIN job_eligibility_criteria ec ON ec.job_id = j.job_id
                WHERE j.job_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new ApplicationEligibilityContext(
                        resultSet.getLong("candidate_id"),
                        resultSet.getBoolean("is_active"),
                        resultSet.getString("qualification"),
                        resultSet.getInt("passing_year"),
                        resultSet.getBigDecimal("percentage"),
                        resultSet.getInt("backlog_count"),
                        resultSet.getString("skills"),
                        resultSet.getLong("job_id"),
                        resultSet.getLong("company_id"),
                        resultSet.getObject("created_by_user_id", Long.class),
                        resultSet.getString("status"),
                        resultSet.getDate("application_deadline").toLocalDate(),
                        resultSet.getString("required_qualification"),
                        resultSet.getString("required_skills"),
                        resultSet.getBigDecimal("minimum_percentage"),
                        resultSet.getObject("minimum_passing_year", Integer.class),
                        resultSet.getObject("maximum_passing_year", Integer.class),
                        resultSet.getInt("maximum_backlogs")),
                candidateId,
                jobId);
    }

    public long insert(long candidateId, long jobId, long changedByUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO applications (candidate_id, job_id, status)
                    VALUES (?, ?, 'APPLIED')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, candidateId);
            statement.setLong(2, jobId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created application ID");
        }
        jdbcTemplate.update("""
                INSERT INTO application_status_history (
                    application_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, NULL, 'APPLIED', ?, ?)
                """,
                key.longValue(),
                changedByUserId,
                "Application submitted");
        return key.longValue();
    }

    public ApplicationResponse findById(long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + APPLICATION_COLUMNS + APPLICATION_FROM + " WHERE a.application_id = ?",
                APPLICATION_ROW_MAPPER,
                applicationId);
    }

    public ApplicationPageResponse findAll(
            String search,
            String candidate,
            String company,
            String job,
            ApplicationStatus status,
            Integer passingYear,
            Long assignedRecruiterId,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(search)) {
            String pattern = containsPattern(search);
            where.append("""
                     AND (LOWER(c.full_name) LIKE ? OR LOWER(co.company_name) LIKE ?
                     OR LOWER(j.job_title) LIKE ?)
                    """);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "c.full_name", candidate);
        addLikeFilter(where, parameters, "co.company_name", company);
        addLikeFilter(where, parameters, "j.job_title", job);
        if (status != null) {
            where.append(" AND a.status = ?");
            parameters.add(status.name());
        }
        if (passingYear != null) {
            where.append(" AND c.passing_year = ?");
            parameters.add(passingYear);
        }
        if (assignedRecruiterId != null) {
            where.append(" AND j.created_by_user_id = ?");
            parameters.add(assignedRecruiterId);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + APPLICATION_FROM + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<ApplicationResponse> applications = jdbcTemplate.query(
                "SELECT " + APPLICATION_COLUMNS + APPLICATION_FROM + where
                        + " ORDER BY a.application_id LIMIT ? OFFSET ?",
                APPLICATION_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new ApplicationPageResponse(applications, page, size, total, totalPages);
    }

    public ApplicationState lockState(long applicationId) {
        return jdbcTemplate.queryForObject("""
                SELECT a.status, j.created_by_user_id
                FROM applications a
                JOIN jobs j ON j.job_id = a.job_id
                WHERE a.application_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new ApplicationState(
                        ApplicationStatus.valueOf(resultSet.getString("status")),
                        resultSet.getObject("created_by_user_id", Long.class)),
                applicationId);
    }

    public Long findAssignedRecruiterId(long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT j.created_by_user_id FROM applications a JOIN jobs j ON j.job_id = a.job_id "
                        + "WHERE a.application_id = ?",
                Long.class,
                applicationId);
    }

    public void updateStatus(
            long applicationId,
            ApplicationStatus previousStatus,
            ApplicationStatus newStatus,
            long changedByUserId,
            String reason) {
        jdbcTemplate.update(
                "UPDATE applications SET status = ? WHERE application_id = ?",
                newStatus.name(),
                applicationId);
        jdbcTemplate.update("""
                INSERT INTO application_status_history (
                    application_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                applicationId,
                previousStatus.name(),
                newStatus.name(),
                changedByUserId,
                normalize(reason));
    }

    public List<ApplicationStatusHistoryResponse> findHistory(long applicationId) {
        return jdbcTemplate.query("""
                SELECT history_id, previous_status, new_status, changed_by_user_id,
                       change_reason, changed_at
                FROM application_status_history
                WHERE application_id = ?
                ORDER BY changed_at, history_id
                """,
                (resultSet, rowNumber) -> new ApplicationStatusHistoryResponse(
                        resultSet.getLong("history_id"),
                        resultSet.getString("previous_status") == null
                                ? null
                                : ApplicationStatus.valueOf(resultSet.getString("previous_status")),
                        ApplicationStatus.valueOf(resultSet.getString("new_status")),
                        resultSet.getObject("changed_by_user_id", Long.class),
                        resultSet.getString("change_reason"),
                        timestampToLocalDateTime(resultSet.getTimestamp("changed_at"))),
                applicationId);
    }

    private static void addLikeFilter(
            StringBuilder where,
            List<Object> parameters,
            String column,
            String value) {
        if (hasText(value)) {
            where.append(" AND LOWER(").append(column).append(") LIKE ?");
            parameters.add(containsPattern(value));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static java.time.LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record ApplicationState(ApplicationStatus status, Long assignedRecruiterId) {
    }
}
