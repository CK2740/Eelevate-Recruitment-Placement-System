package com.recruitment.dao;

import com.recruitment.dto.OfferPageResponse;
import com.recruitment.dto.OfferRequest;
import com.recruitment.dto.OfferResponse;
import com.recruitment.dto.OfferStatus;
import com.recruitment.dto.OfferStatusHistoryResponse;
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
public class OfferDao {

    private static final String OFFER_COLUMNS = """
            o.offer_id, o.application_id, a.candidate_id, c.full_name AS candidate_name,
            co.company_id, co.company_name, o.designation, o.package_amount,
            o.offer_date, o.expiry_date, o.joining_date, o.offer_details, o.status,
            o.created_at, o.updated_at
            """;

    private static final String OFFER_FROM = """
             FROM offers o
             JOIN applications a ON a.application_id = o.application_id
             JOIN candidates c ON c.candidate_id = a.candidate_id
             JOIN jobs j ON j.job_id = a.job_id
             JOIN companies co ON co.company_id = j.company_id
            """;

    private static final RowMapper<OfferResponse> OFFER_ROW_MAPPER =
            (resultSet, rowNumber) -> new OfferResponse(
                    resultSet.getLong("offer_id"),
                    resultSet.getLong("application_id"),
                    resultSet.getLong("candidate_id"),
                    resultSet.getString("candidate_name"),
                    resultSet.getLong("company_id"),
                    resultSet.getString("company_name"),
                    resultSet.getString("designation"),
                    resultSet.getBigDecimal("package_amount"),
                    resultSet.getDate("offer_date").toLocalDate(),
                    resultSet.getDate("expiry_date") == null
                            ? null
                            : resultSet.getDate("expiry_date").toLocalDate(),
                    resultSet.getDate("joining_date") == null
                            ? null
                            : resultSet.getDate("joining_date").toLocalDate(),
                    resultSet.getString("offer_details"),
                    OfferStatus.valueOf(resultSet.getString("status")),
                    timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public OfferDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OfferContext lockApplication(long applicationId) {
        return jdbcTemplate.queryForObject("""
                SELECT a.application_id, a.status AS application_status, j.created_by_user_id
                FROM applications a
                JOIN jobs j ON j.job_id = a.job_id
                WHERE a.application_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new OfferContext(
                        resultSet.getLong("application_id"),
                        resultSet.getString("application_status"),
                        resultSet.getObject("created_by_user_id", Long.class)),
                applicationId);
    }

    public OfferContext lockOffer(long offerId) {
        return jdbcTemplate.queryForObject("""
                SELECT o.offer_id, o.application_id, o.status, o.expiry_date, o.joining_date,
                       o.designation, o.package_amount, j.created_by_user_id, a.status AS application_status
                FROM offers o
                JOIN applications a ON a.application_id = o.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE o.offer_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new OfferContext(
                        resultSet.getLong("offer_id"),
                        resultSet.getLong("application_id"),
                        OfferStatus.valueOf(resultSet.getString("status")),
                        resultSet.getDate("expiry_date") == null
                                ? null
                                : resultSet.getDate("expiry_date").toLocalDate(),
                        resultSet.getDate("joining_date") == null
                                ? null
                                : resultSet.getDate("joining_date").toLocalDate(),
                        resultSet.getString("designation"),
                        resultSet.getBigDecimal("package_amount"),
                        resultSet.getObject("created_by_user_id", Long.class),
                        resultSet.getString("application_status")),
                offerId);
    }

    public long insert(long applicationId, OfferRequest request, long createdByUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO offers (
                        application_id, designation, package_amount, offer_date,
                        expiry_date, offer_details, status
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, applicationId);
            statement.setString(2, request.designation().trim());
            statement.setBigDecimal(3, request.packageAmount());
            statement.setDate(4, Date.valueOf(request.offerDate()));
            statement.setDate(5, Date.valueOf(request.expiryDate()));
            statement.setString(6, normalize(request.offerDetails()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created offer ID");
        }
        insertHistory(key.longValue(), null, OfferStatus.PENDING, createdByUserId, "Offer created");
        return key.longValue();
    }

    public boolean updateStatus(
            long offerId,
            OfferStatus previousStatus,
            OfferStatus newStatus,
            java.time.LocalDate joiningDate,
            long changedByUserId,
            String reason) {
        int updated = jdbcTemplate.update("""
                UPDATE offers SET status = ?, joining_date = COALESCE(?, joining_date)
                WHERE offer_id = ? AND status = ?
                """,
                newStatus.name(),
                joiningDate == null ? null : Date.valueOf(joiningDate),
                offerId,
                previousStatus.name());
        if (updated == 0) {
            return false;
        }
        insertHistory(offerId, previousStatus, newStatus, changedByUserId, reason);
        return true;
    }

    public boolean updateDetails(long offerId, OfferRequest request) {
        return jdbcTemplate.update("""
                UPDATE offers
                SET designation = ?, package_amount = ?, offer_date = ?,
                    expiry_date = ?, offer_details = ?
                WHERE offer_id = ? AND status = 'PENDING'
                """,
                request.designation().trim(),
                request.packageAmount(),
                Date.valueOf(request.offerDate()),
                Date.valueOf(request.expiryDate()),
                normalize(request.offerDetails()),
                offerId) > 0;
    }

    public void updateJoiningDate(long offerId, java.time.LocalDate joiningDate, long changedByUserId) {
        jdbcTemplate.update(
                "UPDATE offers SET joining_date = ? WHERE offer_id = ? AND status = 'ACCEPTED'",
                Date.valueOf(joiningDate),
                offerId);
        insertHistory(
                offerId,
                OfferStatus.ACCEPTED,
                OfferStatus.ACCEPTED,
                changedByUserId,
                "Joining date recorded: " + joiningDate);
    }

    public OfferResponse findById(long offerId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + OFFER_COLUMNS + OFFER_FROM + " WHERE o.offer_id = ?",
                OFFER_ROW_MAPPER,
                offerId);
    }

    public OfferPageResponse findAll(
            String search,
            String candidate,
            String company,
            String designation,
            OfferStatus status,
            java.time.LocalDate expiryBefore,
            Long recruiterId,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(search)) {
            String pattern = containsPattern(search);
            where.append("""
                     AND (LOWER(c.full_name) LIKE ? OR LOWER(co.company_name) LIKE ?
                     OR LOWER(o.designation) LIKE ?)
                    """);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "c.full_name", candidate);
        addLikeFilter(where, parameters, "co.company_name", company);
        addLikeFilter(where, parameters, "o.designation", designation);
        if (status != null) {
            where.append(" AND o.status = ?");
            parameters.add(status.name());
        }
        if (expiryBefore != null) {
            where.append(" AND o.expiry_date <= ?");
            parameters.add(Date.valueOf(expiryBefore));
        }
        if (recruiterId != null) {
            where.append(" AND j.created_by_user_id = ?");
            parameters.add(recruiterId);
        }
        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + OFFER_FROM + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<OfferResponse> offers = jdbcTemplate.query(
                "SELECT " + OFFER_COLUMNS + OFFER_FROM + where
                        + " ORDER BY o.offer_id DESC LIMIT ? OFFSET ?",
                OFFER_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new OfferPageResponse(offers, page, size, total, totalPages);
    }

    public List<OfferStatusHistoryResponse> findHistory(long offerId) {
        return jdbcTemplate.query("""
                SELECT history_id, previous_status, new_status, changed_by_user_id,
                       change_reason, changed_at
                FROM offer_status_history
                WHERE offer_id = ?
                ORDER BY changed_at, history_id
                """,
                (resultSet, rowNumber) -> new OfferStatusHistoryResponse(
                        resultSet.getLong("history_id"),
                        resultSet.getString("previous_status") == null
                                ? null
                                : OfferStatus.valueOf(resultSet.getString("previous_status")),
                        OfferStatus.valueOf(resultSet.getString("new_status")),
                        resultSet.getObject("changed_by_user_id", Long.class),
                        resultSet.getString("change_reason"),
                        timestampToLocalDateTime(resultSet.getTimestamp("changed_at"))),
                offerId);
    }

    public boolean recruiterOwnsOffer(long offerId, long recruiterId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM offers o
                JOIN applications a ON a.application_id = o.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE o.offer_id = ? AND j.created_by_user_id = ?
                """,
                Integer.class,
                offerId,
                recruiterId);
        return count != null && count > 0;
    }

    public boolean hasOffer(long applicationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM offers WHERE application_id = ?",
                Integer.class,
                applicationId);
        return count != null && count > 0;
    }

    public void updateApplicationStatus(
            long applicationId,
            String previousStatus,
            String newStatus,
            long changedByUserId,
            String reason) {
        int updated = jdbcTemplate.update(
                "UPDATE applications SET status = ? WHERE application_id = ? AND status = ?",
                newStatus,
                applicationId,
                previousStatus);
        if (updated > 0) {
            jdbcTemplate.update("""
                    INSERT INTO application_status_history (
                        application_id, previous_status, new_status, changed_by_user_id, change_reason
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    applicationId,
                    previousStatus,
                    newStatus,
                    changedByUserId,
                    normalize(reason));
        }
    }

    private void insertHistory(
            long offerId,
            OfferStatus previousStatus,
            OfferStatus newStatus,
            long changedByUserId,
            String reason) {
        jdbcTemplate.update("""
                INSERT INTO offer_status_history (
                    offer_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                offerId,
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

    public record OfferContext(
            long offerId,
            long applicationId,
            OfferStatus status,
            java.time.LocalDate expiryDate,
            java.time.LocalDate joiningDate,
            String designation,
            java.math.BigDecimal packageAmount,
            Long assignedRecruiterId,
            String applicationStatus) {
        public OfferContext(
                long applicationId,
                String applicationStatus,
                Long assignedRecruiterId) {
            this(0, applicationId, OfferStatus.PENDING, null, null, null, null,
                    assignedRecruiterId, applicationStatus);
        }
    }
}
