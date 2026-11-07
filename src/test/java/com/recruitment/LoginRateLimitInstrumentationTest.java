package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.recruitment.dao.LoginAttemptDao;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(OutputCaptureExtension.class)
class LoginRateLimitInstrumentationTest {

    @Test
    void logsEachLimiterSqlTimingWhenTheDatabaseCallFails(CapturedOutput output) {
        JdbcTemplate insertJdbcTemplate = mock(JdbcTemplate.class);
        doThrow(new IllegalStateException("database failure"))
                .when(insertJdbcTemplate).update(contains("INSERT"), any(Object[].class));
        LoginAttemptDao insertDao = new LoginAttemptDao(insertJdbcTemplate);

        assertThrows(IllegalStateException.class, () ->
                insertDao.lock("IP", new byte[32], LocalDateTime.now()));
        assertThrows(IllegalStateException.class, () ->
                insertDao.lock("EMAIL", new byte[32], LocalDateTime.now()));

        JdbcTemplate selectJdbcTemplate = mock(JdbcTemplate.class);
        when(selectJdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        doThrow(new IllegalStateException("database failure"))
                .when(selectJdbcTemplate)
                .queryForObject(
                        contains("SELECT"),
                        any(RowMapper.class),
                        any(Object[].class));
        LoginAttemptDao selectDao = new LoginAttemptDao(selectJdbcTemplate);

        assertThrows(IllegalStateException.class, () ->
                selectDao.lock("IP", new byte[32], LocalDateTime.now()));
        assertThrows(IllegalStateException.class, () ->
                selectDao.lock("EMAIL", new byte[32], LocalDateTime.now()));

        JdbcTemplate cleanupJdbcTemplate = mock(JdbcTemplate.class);
        doThrow(new IllegalStateException("database failure"))
                .when(cleanupJdbcTemplate).update(contains("DELETE"), any(Object[].class));
        LoginAttemptDao cleanupDao = new LoginAttemptDao(cleanupJdbcTemplate);

        assertThrows(IllegalStateException.class, () ->
                cleanupDao.clear("EMAIL", new byte[32]));
        assertThrows(IllegalStateException.class, () ->
                cleanupDao.clearIfNoFailures("IP", new byte[32]));

        for (String operation : new String[] {
                "ip_limiter_insert_attempt",
                "email_limiter_insert_attempt",
                "ip_limiter_select_for_update",
                "email_limiter_select_for_update",
                "ip_limiter_cleanup_delete",
                "email_limiter_cleanup_delete"
        }) {
            assertTrue(
                    output.getOut().contains("operation=" + operation + " duration_ms="),
                    "Missing duration for " + operation);
        }
        assertTrue(
                !output.getOut().contains("192.0.2.") && !output.getOut().contains("@example.com"),
                "Timing output must not contain request identifiers");
    }
}
