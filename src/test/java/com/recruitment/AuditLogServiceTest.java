package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dao.AuditLogDao;
import com.recruitment.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogDao auditLogDao;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void persistsUserActionAndJsonDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", "PUT");
        details.put("path", "/api/jobs/33");
        details.put("technology", "Java");
        auditLogService.log(
                12L,
                "JOB_UPDATED",
                "JOB",
                33L,
                details);

        verify(auditLogDao).insert(
                12L,
                "JOB_UPDATED",
                "JOB",
                33L,
                "{\"method\":\"PUT\",\"path\":\"/api/jobs/33\",\"technology\":\"Java\"}");
    }

    @Test
    void rejectsInvalidAuditDateRange() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> auditLogService.getLogs(
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 2, 1, 0, 0),
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        0,
                        20));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void validatesLogPagination() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> auditLogService.getLogs(null, null, null, null, null, 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
