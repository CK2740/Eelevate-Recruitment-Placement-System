package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.recruitment.dao.LoginAttemptDao;
import com.recruitment.dto.LoginRequest;
import com.recruitment.service.AuthenticationService;
import com.recruitment.service.LoginRateLimitService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class LoginRateLimitServiceTest {

    private MutableClock clock;
    private AtomicBoolean credentialsAccepted;
    private LoginRateLimitService loginRateLimitService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:login-rate-limit-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE login_attempt_tracking (
                    identifier_type VARCHAR(8) NOT NULL,
                    identifier_hash BINARY(32) NOT NULL,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    window_started_at DATETIME(6) NULL,
                    blocked_until DATETIME(6) NULL,
                    updated_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (identifier_type, identifier_hash)
                )
                """);

        AuthenticationManager authenticationManager = org.mockito.Mockito.mock(AuthenticationManager.class);
        credentialsAccepted = new AtomicBoolean(false);
        given(authenticationManager.authenticate(any(Authentication.class))).willAnswer(invocation -> {
            Authentication request = invocation.getArgument(0);
            if (!credentialsAccepted.get()) {
                throw new BadCredentialsException("Invalid credentials");
            }
            return new TestingAuthenticationToken(
                    request.getPrincipal(),
                    null,
                    "ROLE_CANDIDATE");
        });

        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthenticationService authenticationService = new AuthenticationService(authenticationManager);
        loginRateLimitService = new LoginRateLimitService(
                new LoginAttemptDao(jdbcTemplate),
                authenticationService,
                new DataSourceTransactionManager(dataSource),
                clock);
    }

    @Test
    void successfulAuthenticationIsReturned() {
        credentialsAccepted.set(true);

        Authentication authentication = loginRateLimitService.authenticate(
                new LoginRequest("Candidate@Example.com", "password"), "192.0.2.1");

        assertNotNull(authentication);
        assertEquals("ROLE_CANDIDATE", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void invalidCredentialsHaveTheSameGenericResponse() {
        ResponseStatusException unknownEmail = assertThrows(
                ResponseStatusException.class,
                () -> loginRateLimitService.authenticate(
                        new LoginRequest("unknown@example.com", "wrong"), "192.0.2.2"));
        ResponseStatusException wrongPassword = assertThrows(
                ResponseStatusException.class,
                () -> loginRateLimitService.authenticate(
                        new LoginRequest("known@example.com", "wrong"), "192.0.2.3"));

        assertEquals(HttpStatus.UNAUTHORIZED, unknownEmail.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
        assertEquals(unknownEmail.getReason(), wrongPassword.getReason());
        assertEquals("Invalid email or password", unknownEmail.getReason());
    }

    @Test
    void fifthFailureTriggersTemporaryBlockOnSubsequentAttempts() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED, attempt("blocked@example.com", "192.0.2.4"));
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, attempt("blocked@example.com", "192.0.2.4"));
    }

    @Test
    void blockAndFailureWindowExpireWithoutWaiting() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED, attempt("expires@example.com", "192.0.2.5"));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, attempt("expires@example.com", "192.0.2.5"));

        clock.advance(Duration.ofMinutes(15));

        assertEquals(HttpStatus.UNAUTHORIZED, attempt("expires@example.com", "192.0.2.5"));
    }

    @Test
    void successfulAuthenticationResetsAccountFailureTracking() {
        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    attempt("reset@example.com", "192.0.2." + (10 + attempt)));
        }

        credentialsAccepted.set(true);
        assertEquals(HttpStatus.OK, attempt("reset@example.com", "192.0.2.20"));
        credentialsAccepted.set(false);

        assertEquals(HttpStatus.UNAUTHORIZED, attempt("reset@example.com", "192.0.2.21"));
        assertEquals(HttpStatus.UNAUTHORIZED, attempt("reset@example.com", "192.0.2.22"));
    }

    @Test
    void ipAttemptsAreLimitedAcrossDifferentEmails() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    attempt("person" + attempt + "@example.com", "192.0.2.30"));
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, attempt("another@example.com", "192.0.2.30"));
    }

    @Test
    void separateUsersHaveIndependentAccountLimits() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.UNAUTHORIZED,
                    attempt("first@example.com", "198.51.100." + (attempt + 1)));
        }

        assertEquals(HttpStatus.UNAUTHORIZED, attempt("second@example.com", "198.51.100.10"));
    }

    @Test
    void concurrentRequestsCannotExceedFiveFailedAttemptsBeforeBlocking() throws Exception {
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> results = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attempt("parallel@example.com", "203.0.113.1");
                }));
            }

            ready.await();
            start.countDown();
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<HttpStatus> result : results) {
                statuses.add(result.get());
            }

            assertEquals(5, statuses.stream().filter(HttpStatus.UNAUTHORIZED::equals).count());
            assertEquals(15, statuses.stream().filter(HttpStatus.TOO_MANY_REQUESTS::equals).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpStatus attempt(String email, String ipAddress) {
        try {
            loginRateLimitService.authenticate(new LoginRequest(email, "wrong-password"), ipAddress);
            return HttpStatus.OK;
        } catch (ResponseStatusException exception) {
            return HttpStatus.valueOf(exception.getStatusCode().value());
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
