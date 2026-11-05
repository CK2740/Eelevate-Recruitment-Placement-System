package com.recruitment.dao;

import com.recruitment.dto.CandidatePageResponse;
import com.recruitment.dto.CandidateRequest;
import com.recruitment.dto.CandidateResponse;
import com.recruitment.dto.CandidateStatus;
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
public class CandidateDao {

    private static final String CANDIDATE_COLUMNS = """
            candidate_id, full_name, email, mobile, qualification, passing_year,
            percentage, backlog_count, skills, is_active, created_at, updated_at
            """;

    private static final RowMapper<CandidateResponse> CANDIDATE_ROW_MAPPER =
            (resultSet, rowNumber) -> new CandidateResponse(
                    resultSet.getLong("candidate_id"),
                    resultSet.getString("full_name"),
                    resultSet.getString("email"),
                    resultSet.getString("mobile"),
                    resultSet.getString("qualification"),
                    resultSet.getInt("passing_year"),
                    resultSet.getBigDecimal("percentage"),
                    resultSet.getInt("backlog_count"),
                    resultSet.getString("skills"),
                    resultSet.getBoolean("is_active")
                            ? CandidateStatus.ACTIVE
                            : CandidateStatus.INACTIVE,
                    timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public CandidateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CandidateResponse findById(long candidateId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + CANDIDATE_COLUMNS + " FROM candidates WHERE candidate_id = ?",
                CANDIDATE_ROW_MAPPER,
                candidateId);
    }

    public CandidatePageResponse findAll(
            String search,
            String name,
            String email,
            String technology,
            Integer passingYear,
            CandidateStatus status,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" FROM candidates WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (hasText(search)) {
            where.append(" AND (LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(skills) LIKE ?)");
            String pattern = containsPattern(search);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "full_name", name);
        addLikeFilter(where, parameters, "email", email);
        addLikeFilter(where, parameters, "skills", technology);
        if (passingYear != null) {
            where.append(" AND passing_year = ?");
            parameters.add(passingYear);
        }
        if (status != null) {
            where.append(" AND is_active = ?");
            parameters.add(status == CandidateStatus.ACTIVE);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<CandidateResponse> candidates = jdbcTemplate.query(
                "SELECT " + CANDIDATE_COLUMNS + where
                        + " ORDER BY candidate_id LIMIT ? OFFSET ?",
                CANDIDATE_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new CandidatePageResponse(candidates, page, size, total, totalPages);
    }

    public long insert(CandidateRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO candidates (
                        full_name, email, mobile, qualification, passing_year,
                        percentage, backlog_count, skills
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setCandidateParameters(statement, request);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created candidate ID");
        }
        return key.longValue();
    }

    public boolean update(long candidateId, CandidateRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE candidates
                SET full_name = ?, email = ?, mobile = ?, qualification = ?, passing_year = ?,
                    percentage = ?, backlog_count = ?, skills = ?
                WHERE candidate_id = ?
                """,
                request.fullName().trim(),
                normalizeEmail(request.email()),
                request.mobile().trim(),
                request.qualification().trim(),
                request.passingYear(),
                request.percentage(),
                request.backlogCount(),
                normalizeSkills(request.skills()),
                candidateId);
        return updated > 0;
    }

    public boolean delete(long candidateId) {
        return jdbcTemplate.update(
                "DELETE FROM candidates WHERE candidate_id = ?",
                candidateId) > 0;
    }

    public boolean hasApplications(long candidateId) {
        Integer applicationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ?",
                Integer.class,
                candidateId);
        return applicationCount != null && applicationCount > 0;
    }

    public boolean updateStatus(long candidateId, CandidateStatus status) {
        return jdbcTemplate.update(
                "UPDATE candidates SET is_active = ? WHERE candidate_id = ?",
                status == CandidateStatus.ACTIVE,
                candidateId) > 0;
    }

    private static void setCandidateParameters(
            java.sql.PreparedStatement statement,
            CandidateRequest request) throws java.sql.SQLException {
        statement.setString(1, request.fullName().trim());
        statement.setString(2, normalizeEmail(request.email()));
        statement.setString(3, request.mobile().trim());
        statement.setString(4, request.qualification().trim());
        statement.setInt(5, request.passingYear());
        statement.setBigDecimal(6, request.percentage());
        statement.setInt(7, request.backlogCount());
        statement.setString(8, normalizeSkills(request.skills()));
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSkills(String skills) {
        return hasText(skills) ? skills.trim() : null;
    }

    private static java.time.LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
