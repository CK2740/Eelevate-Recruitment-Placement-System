package com.recruitment.dao;

import com.recruitment.util.LoginPerformanceMetrics;
import com.recruitment.dto.AuditLogPageResponse;
import com.recruitment.dto.AuditLogResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogDao {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String detailsJson) {
        boolean loginAudit = "USER_LOGGED_IN".equals(action);
        long startedAt = loginAudit ? LoginPerformanceMetrics.startTimer() : 0L;
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, new_values)
                    VALUES (?, ?, ?, ?, ?)
                    """, userId, action, entityType, entityId, detailsJson);
        } finally {
            if (loginAudit) {
                LoginPerformanceMetrics.logDuration("login_audit_insert", startedAt);
            }
        }
    }

    public AuditLogPageResponse find(
            Long userId,
            String action,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (userId != null) {
            where.append(" AND al.user_id = ?");
            parameters.add(userId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND al.action = ?");
            parameters.add(action.trim());
        }
        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND al.entity_type = ?");
            parameters.add(entityType.trim());
        }
        if (from != null) {
            where.append(" AND al.created_at >= ?");
            parameters.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND al.created_at <= ?");
            parameters.add(Timestamp.valueOf(to));
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs al" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<AuditLogResponse> items = jdbcTemplate.query("""
                SELECT al.audit_log_id, al.user_id, u.username, al.action, al.entity_type,
                       al.entity_id, CAST(al.new_values AS CHAR) AS details, al.created_at
                FROM audit_logs al
                LEFT JOIN users u ON u.user_id = al.user_id
                """ + where + " ORDER BY al.created_at DESC, al.audit_log_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    long rawUserId = rs.getLong("user_id");
                    Long rowUserId = rs.wasNull() ? null : rawUserId;
                    long rawEntityId = rs.getLong("entity_id");
                    Long rowEntityId = rs.wasNull() ? null : rawEntityId;
                    return new AuditLogResponse(
                            rs.getLong("audit_log_id"),
                            rowUserId,
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("entity_type"),
                            rowEntityId,
                            rs.getString("details"),
                            rs.getTimestamp("created_at").toLocalDateTime());
                },
                pageParameters.toArray());
        long count = total == null ? 0 : total;
        return new AuditLogPageResponse(
                items,
                page,
                size,
                count,
                count == 0 ? 0 : (int) ((count + size - 1) / size));
    }
}
