package com.recruitment.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptDao {

    private final JdbcTemplate jdbcTemplate;

    public LoginAttemptDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AttemptState lock(String identifierType, byte[] identifierHash, LocalDateTime now) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO login_attempt_tracking (
                        identifier_type, identifier_hash, failed_attempts, updated_at
                    ) VALUES (?, ?, 0, ?)
                    """, identifierType, identifierHash, Timestamp.valueOf(now));
        } catch (DuplicateKeyException ignored) {
            // The existing row is locked below; this also serializes first-use races.
        }

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
        jdbcTemplate.update("""
                DELETE FROM login_attempt_tracking
                WHERE identifier_type = ? AND identifier_hash = ?
                """, identifierType, identifierHash);
    }

    public void clearIfNoFailures(String identifierType, byte[] identifierHash) {
        jdbcTemplate.update("""
                DELETE FROM login_attempt_tracking
                WHERE identifier_type = ? AND identifier_hash = ? AND failed_attempts = 0
                """, identifierType, identifierHash);
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
