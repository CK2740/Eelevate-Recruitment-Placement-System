package com.recruitment.dao;

import com.recruitment.util.LoginPerformanceMetrics;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptDao {

    private static final String IP = "IP";
    private static final String IP_INSERT_OPERATION = "ip_limiter_insert_attempt";
    private static final String EMAIL_INSERT_OPERATION = "email_limiter_insert_attempt";
    private static final String IP_SELECT_OPERATION = "ip_limiter_select_for_update";
    private static final String EMAIL_SELECT_OPERATION = "email_limiter_select_for_update";
    private static final String IP_CLEANUP_OPERATION = "ip_limiter_cleanup_delete";
    private static final String EMAIL_CLEANUP_OPERATION = "email_limiter_cleanup_delete";

    private final JdbcTemplate jdbcTemplate;

    public LoginAttemptDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AttemptState lock(String identifierType, byte[] identifierHash, LocalDateTime now) {
        String insertOperation = operationForIdentifier(
                identifierType, IP_INSERT_OPERATION, EMAIL_INSERT_OPERATION);
        long insertStartedAt = LoginPerformanceMetrics.startTimer();
        try {
            try {
                jdbcTemplate.update("""
                        INSERT INTO login_attempt_tracking (
                            identifier_type, identifier_hash, failed_attempts, updated_at
                        ) VALUES (?, ?, 0, ?)
                        """, identifierType, identifierHash, Timestamp.valueOf(now));
            } catch (DuplicateKeyException ignored) {
                // The existing row is locked below; this also serializes first-use races.
            }
        } finally {
            LoginPerformanceMetrics.logDuration(insertOperation, insertStartedAt);
        }

        String selectOperation = operationForIdentifier(
                identifierType, IP_SELECT_OPERATION, EMAIL_SELECT_OPERATION);
        long selectStartedAt = LoginPerformanceMetrics.startTimer();
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT failed_attempts, window_started_at, blocked_until
                    FROM login_attempt_tracking
                    WHERE identifier_type = ? AND identifier_hash = ?
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> {
                        Timestamp windowStartedAt = resultSet.getTimestamp("window_started_at");
                        Timestamp blockedUntil = resultSet.getTimestamp("blocked_until");
                        return new AttemptState(
                                resultSet.getInt("failed_attempts"),
                                windowStartedAt == null ? null : windowStartedAt.toLocalDateTime(),
                                blockedUntil == null ? null : blockedUntil.toLocalDateTime());
                    },
                    identifierType,
                    identifierHash);
        } finally {
            LoginPerformanceMetrics.logDuration(selectOperation, selectStartedAt);
        }
    }

    public void recordFailure(
            String identifierType,
            byte[] identifierHash,
            int failedAttempts,
            LocalDateTime windowStartedAt,
            LocalDateTime blockedUntil,
            LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                UPDATE login_attempt_tracking
                SET failed_attempts = ?, window_started_at = ?, blocked_until = ?, updated_at = ?
                WHERE identifier_type = ? AND identifier_hash = ?
                """,
                failedAttempts,
                Timestamp.valueOf(windowStartedAt),
                blockedUntil == null ? null : Timestamp.valueOf(blockedUntil),
                Timestamp.valueOf(updatedAt),
                identifierType,
                identifierHash);
    }

    public void resetWindow(String identifierType, byte[] identifierHash, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                UPDATE login_attempt_tracking
                SET failed_attempts = 0, window_started_at = NULL, blocked_until = NULL, updated_at = ?
                WHERE identifier_type = ? AND identifier_hash = ?
                """, Timestamp.valueOf(updatedAt), identifierType, identifierHash);
    }

    public void clear(String identifierType, byte[] identifierHash) {
        long cleanupStartedAt = LoginPerformanceMetrics.startTimer();
        try {
            jdbcTemplate.update("""
                    DELETE FROM login_attempt_tracking
                    WHERE identifier_type = ? AND identifier_hash = ?
                    """, identifierType, identifierHash);
        } finally {
            LoginPerformanceMetrics.logDuration(
                    operationForIdentifier(identifierType, IP_CLEANUP_OPERATION, EMAIL_CLEANUP_OPERATION),
                    cleanupStartedAt);
        }
    }

    public void clearIfNoFailures(String identifierType, byte[] identifierHash) {
        long cleanupStartedAt = LoginPerformanceMetrics.startTimer();
        try {
            jdbcTemplate.update("""
                    DELETE FROM login_attempt_tracking
                    WHERE identifier_type = ? AND identifier_hash = ? AND failed_attempts = 0
                    """, identifierType, identifierHash);
        } finally {
            LoginPerformanceMetrics.logDuration(
                    operationForIdentifier(identifierType, IP_CLEANUP_OPERATION, EMAIL_CLEANUP_OPERATION),
                    cleanupStartedAt);
        }
    }

    private static String operationForIdentifier(
            String identifierType,
            String ipOperation,
            String emailOperation) {
        return IP.equals(identifierType) ? ipOperation : emailOperation;
    }

    public int deleteExpiredBatch() {
        return jdbcTemplate.update("""
                DELETE FROM login_attempt_tracking
                WHERE updated_at < UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE
                ORDER BY updated_at
                LIMIT 5000
                """);
    }

    public record AttemptState(
            int failedAttempts,
            LocalDateTime windowStartedAt,
            LocalDateTime blockedUntil) {
    }
}
