package com.recruitment.dao;

import com.recruitment.dto.PlacementPageResponse;
import com.recruitment.dto.PlacementRequest;
import com.recruitment.dto.PlacementResponse;
import com.recruitment.dto.PlacementStatus;
import com.recruitment.dto.PlacementStatusHistoryResponse;
import java.sql.Date;
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
public class PlacementDao {

    private static final String PLACEMENT_COLUMNS = """
            p.placement_id, p.application_id, a.candidate_id, c.full_name AS candidate_name,
            co.company_id, co.company_name, p.designation, p.final_package,
            p.actual_joining_date, p.joining_status, p.created_at, p.updated_at
            """;

    private static final String PLACEMENT_FROM = """
             FROM placements p
             JOIN applications a ON a.application_id = p.application_id
             JOIN candidates c ON c.candidate_id = a.candidate_id
             JOIN jobs j ON j.job_id = a.job_id
             JOIN companies co ON co.company_id = j.company_id
            """;

    private static final RowMapper<PlacementResponse> PLACEMENT_ROW_MAPPER =
            (resultSet, rowNumber) -> new PlacementResponse(
                    resultSet.getLong("placement_id"),
                    resultSet.getLong("application_id"),
                    resultSet.getLong("candidate_id"),
                    resultSet.getString("candidate_name"),
                    resultSet.getLong("company_id"),
                    resultSet.getString("company_name"),
                    resultSet.getString("designation"),
                    resultSet.getBigDecimal("final_package"),
                    resultSet.getDate("actual_joining_date") == null
                            ? null
                            : resultSet.getDate("actual_joining_date").toLocalDate(),
                    PlacementStatus.valueOf(resultSet.getString("joining_status")),
                    timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public PlacementDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PlacementContext lockApplication(long applicationId) {
        return jdbcTemplate.queryForObject("""
                SELECT a.application_id, a.status AS application_status, j.created_by_user_id,
                       o.status AS offer_status, o.joining_date, o.designation, o.package_amount
                FROM applications a
                JOIN jobs j ON j.job_id = a.job_id
                LEFT JOIN offers o ON o.application_id = a.application_id
                WHERE a.application_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new PlacementContext(
                        resultSet.getLong("application_id"),
                        resultSet.getString("application_status"),
                        resultSet.getObject("created_by_user_id", Long.class),
                        resultSet.getString("offer_status"),
                        resultSet.getDate("joining_date") == null
                                ? null
                                : resultSet.getDate("joining_date").toLocalDate(),
                        resultSet.getString("designation"),
                        resultSet.getBigDecimal("package_amount")),
                applicationId);
    }

    public PlacementContext lockPlacement(long placementId) {
        return jdbcTemplate.queryForObject("""
                SELECT p.placement_id, p.application_id, p.joining_status,
                       a.status AS application_status, j.created_by_user_id
                FROM placements p
                JOIN applications a ON a.application_id = p.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE p.placement_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new PlacementContext(
                        resultSet.getLong("application_id"),
                        resultSet.getString("application_status"),
                        resultSet.getObject("created_by_user_id", Long.class),
                        null,
                        null,
                        null,
                        null,
                        resultSet.getLong("placement_id"),
                        PlacementStatus.valueOf(resultSet.getString("joining_status"))),
                placementId);
    }

    public long insert(long applicationId, PlacementRequest request, long createdByUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO placements (
                        application_id, designation, final_package, actual_joining_date, joining_status
                    ) VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, applicationId);
            statement.setString(2, request.designation().trim());
            statement.setBigDecimal(3, request.finalPackage());
            if (request.actualJoiningDate() == null) {
                statement.setNull(4, java.sql.Types.DATE);
            } else {
                statement.setDate(4, Date.valueOf(request.actualJoiningDate()));
            }
            statement.setString(5, request.joiningStatus().name());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created placement ID");
        }
        insertHistory(
                key.longValue(),
                null,
                request.joiningStatus(),
                createdByUserId,
                request.joiningStatus() == PlacementStatus.PENDING
                        ? "Joining pending"
                        : "Candidate joined");
        if (request.joiningStatus() == PlacementStatus.JOINED) {
            int applicationUpdated = jdbcTemplate.update(
                    "UPDATE applications SET status = 'JOINED' WHERE application_id = ? AND status = 'OFFERED'",
                    applicationId);
            if (applicationUpdated > 0) {
                jdbcTemplate.update("""
                    INSERT INTO application_status_history (
                        application_id, previous_status, new_status, changed_by_user_id, change_reason
                    ) VALUES (?, 'OFFERED', 'JOINED', ?, 'Candidate joined')
                    """,
                        applicationId,
                        createdByUserId);
            }
        }
        return key.longValue();
    }

    public boolean update(
            long placementId,
            PlacementStatus previousStatus,
            PlacementRequest request,
            long changedByUserId,
            String reason) {
        int updated = jdbcTemplate.update("""
                UPDATE placements
                SET designation = ?, final_package = ?, actual_joining_date = ?,
                    joining_status = ?
                WHERE placement_id = ? AND joining_status = ?
                """,
                request.designation().trim(),
                request.finalPackage(),
                request.actualJoiningDate() == null ? null : Date.valueOf(request.actualJoiningDate()),
                request.joiningStatus().name(),
                placementId,
                previousStatus.name());
        if (updated == 0) {
            return false;
        }
        if (previousStatus != request.joiningStatus()) {
            insertHistory(
                    placementId,
                    previousStatus,
                    request.joiningStatus(),
                    changedByUserId,
                    reason);
            if (request.joiningStatus() == PlacementStatus.JOINED) {
                int applicationUpdated = jdbcTemplate.update(
                        "UPDATE applications SET status = 'JOINED' WHERE application_id = ? AND status = 'OFFERED'",
                        getApplicationId(placementId));
                if (applicationUpdated > 0) {
                    jdbcTemplate.update("""
                            INSERT INTO application_status_history (
                                application_id, previous_status, new_status, changed_by_user_id, change_reason
                            ) VALUES (?, 'OFFERED', 'JOINED', ?, ?)
                            """,
                            getApplicationId(placementId),
                            changedByUserId,
                            normalize(reason));
                }
            }
        }
        return true;
    }

    public PlacementResponse findById(long placementId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + PLACEMENT_COLUMNS + PLACEMENT_FROM + " WHERE p.placement_id = ?",
                PLACEMENT_ROW_MAPPER,
                placementId);
    }

    public PlacementPageResponse findAll(
            String search,
            String candidate,
            String company,
            String designation,
            PlacementStatus status,
            Long recruiterId,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(search)) {
            String pattern = containsPattern(search);
            where.append("""
                     AND (LOWER(c.full_name) LIKE ? OR LOWER(co.company_name) LIKE ?
                     OR LOWER(p.designation) LIKE ?)
                    """);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "c.full_name", candidate);
        addLikeFilter(where, parameters, "co.company_name", company);
        addLikeFilter(where, parameters, "p.designation", designation);
        if (status != null) {
            where.append(" AND p.joining_status = ?");
            parameters.add(status.name());
        }
        if (recruiterId != null) {
            where.append(" AND j.created_by_user_id = ?");
            parameters.add(recruiterId);
        }
        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + PLACEMENT_FROM + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<PlacementResponse> placements = jdbcTemplate.query(
                "SELECT " + PLACEMENT_COLUMNS + PLACEMENT_FROM + where
                        + " ORDER BY p.placement_id DESC LIMIT ? OFFSET ?",
                PLACEMENT_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new PlacementPageResponse(placements, page, size, total, totalPages);
    }

    public List<PlacementStatusHistoryResponse> findHistory(long placementId) {
        return jdbcTemplate.query("""
                SELECT history_id, previous_status, new_status, changed_by_user_id,
                       change_reason, changed_at
                FROM placement_status_history
                WHERE placement_id = ?
                ORDER BY changed_at, history_id
                """,
                (resultSet, rowNumber) -> new PlacementStatusHistoryResponse(
                        resultSet.getLong("history_id"),
                        resultSet.getString("previous_status") == null
                                ? null
                                : PlacementStatus.valueOf(resultSet.getString("previous_status")),
                        PlacementStatus.valueOf(resultSet.getString("new_status")),
                        resultSet.getObject("changed_by_user_id", Long.class),
                        resultSet.getString("change_reason"),
                        timestampToLocalDateTime(resultSet.getTimestamp("changed_at"))),
                placementId);
    }

    public boolean recruiterOwnsPlacement(long placementId, long recruiterId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM placements p
                JOIN applications a ON a.application_id = p.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE p.placement_id = ? AND j.created_by_user_id = ?
                """,
                Integer.class,
                placementId,
                recruiterId);
        return count != null && count > 0;
    }

    public boolean placementExists(long applicationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM placements WHERE application_id = ?",
                Integer.class,
                applicationId);
        return count != null && count > 0;
    }

    private long getApplicationId(long placementId) {
        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM placements WHERE placement_id = ?",
                Long.class,
                placementId);
        if (applicationId == null) {
            throw new IllegalStateException("Placement application ID was not found");
        }
        return applicationId;
    }

    private void insertHistory(
            long placementId,
            PlacementStatus previousStatus,
            PlacementStatus newStatus,
            long changedByUserId,
            String reason) {
        jdbcTemplate.update("""
                INSERT INTO placement_status_history (
                    placement_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                placementId,
                previousStatus == null ? null : previousStatus.name(),
                newStatus.name(),
                changedByUserId,
                normalize(reason));
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

    public record PlacementContext(
            long applicationId,
            String applicationStatus,
            Long assignedRecruiterId,
            String offerStatus,
            java.time.LocalDate joiningDate,
            String designation,
            java.math.BigDecimal packageAmount,
            long placementId,
            PlacementStatus joiningStatus) {
        public PlacementContext(
                long applicationId,
                String applicationStatus,
                Long assignedRecruiterId,
                String offerStatus,
                java.time.LocalDate joiningDate,
                String designation,
                java.math.BigDecimal packageAmount) {
            this(applicationId, applicationStatus, assignedRecruiterId, offerStatus,
                    joiningDate, designation, packageAmount, 0, PlacementStatus.PENDING);
        }
    }
}
