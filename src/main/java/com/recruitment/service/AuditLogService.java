package com.recruitment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dao.AuditLogDao;
import com.recruitment.dto.AuditLogPageResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditLogService {

    private final AuditLogDao auditLogDao;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogDao auditLogDao, ObjectMapper objectMapper) {
        this.auditLogDao = auditLogDao;
        this.objectMapper = objectMapper;
    }

    public void log(
            Long userId,
            String action,
            String entityType,
            Long entityId,
            Map<String, ?> details) {
        try {
            auditLogDao.insert(
                    userId,
                    action,
                    entityType,
                    entityId,
                    objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit details", exception);
        }
    }

    public AuditLogPageResponse getLogs(
            Long userId,
            String action,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination values");
        }
        if (userId != null && userId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be positive");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return auditLogDao.find(userId, action, entityType, from, to, page, size);
    }
}
