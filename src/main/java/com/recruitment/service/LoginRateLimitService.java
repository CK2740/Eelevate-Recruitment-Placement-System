package com.recruitment.service;

import com.recruitment.dao.LoginAttemptDao;
import com.recruitment.dao.LoginAttemptDao.AttemptState;
import com.recruitment.dto.LoginRequest;
import com.recruitment.util.LoginPerformanceMetrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginRateLimitService {

    private static final String IP = "IP";
    private static final String EMAIL = "EMAIL";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final String RATE_LIMIT_MESSAGE = "Too many login attempts. Please try again later.";

    private final LoginAttemptDao loginAttemptDao;
    private final AuthenticationService authenticationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public LoginRateLimitService(
            LoginAttemptDao loginAttemptDao,
            AuthenticationService authenticationService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.loginAttemptDao = loginAttemptDao;
        this.authenticationService = authenticationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Authentication authenticate(LoginRequest request, String remoteAddress) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        byte[] emailHash = hash(normalizedEmail);
        byte[] ipHash = hash(remoteAddress);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        long transactionStartedAt = LoginPerformanceMetrics.startTimer();
        Outcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> {
                long transactionBodyStartedAt = LoginPerformanceMetrics.startTimer();
                try {
                    return authenticateWithinTransaction(request, ipHash, emailHash, now);
                } finally {
                    LoginPerformanceMetrics.logDuration(
                            "rate_limit_transaction_body", transactionBodyStartedAt);
                }
            });
        } finally {
            LoginPerformanceMetrics.logDuration("rate_limit_transaction_total", transactionStartedAt);
        }
        if (outcome.rateLimited()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE);
        }
        if (outcome.invalidCredentials()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return outcome.authentication();
    }

    private Outcome authenticateWithinTransaction(
            LoginRequest request,
            byte[] ipHash,
            byte[] emailHash,
            LocalDateTime now) {
        AttemptState ipState = loginAttemptDao.lock(IP, ipHash, now);
        AttemptState emailState = loginAttemptDao.lock(EMAIL, emailHash, now);

        if (isBlocked(ipState, now) || isBlocked(emailState, now)) {
            return Outcome.blocked();
        }

        ipState = resetExpiredWindow(IP, ipHash, ipState, now);
        emailState = resetExpiredWindow(EMAIL, emailHash, emailState, now);

        try {
            Authentication authentication = authenticationService.authenticate(request);
            loginAttemptDao.clear(EMAIL, emailHash);
            loginAttemptDao.clearIfNoFailures(IP, ipHash);
            return Outcome.authenticated(authentication);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw exception;
            }
            recordFailure(IP, ipHash, ipState, now);
            recordFailure(EMAIL, emailHash, emailState, now);
            return Outcome.invalidLogin();
        }
    }

    private AttemptState resetExpiredWindow(
            String identifierType,
            byte[] identifierHash,
            AttemptState state,
            LocalDateTime now) {
        boolean windowExpired = state.windowStartedAt() == null
                || !state.windowStartedAt().plus(WINDOW).isAfter(now);
        if (state.failedAttempts() > 0 && windowExpired) {
            loginAttemptDao.resetWindow(identifierType, identifierHash, now);
            return new AttemptState(0, null, null);
        }
        return state;
    }

    private void recordFailure(
            String identifierType,
            byte[] identifierHash,
            AttemptState state,
            LocalDateTime now) {
        int failures = state.failedAttempts() + 1;
        LocalDateTime windowStartedAt = state.windowStartedAt() == null
                ? now
                : state.windowStartedAt();
        LocalDateTime blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
        loginAttemptDao.recordFailure(
                identifierType, identifierHash, failures, windowStartedAt, blockedUntil, now);
    }

    private boolean isBlocked(AttemptState state, LocalDateTime now) {
        return (state.blockedUntil() != null && state.blockedUntil().isAfter(now))
                || (state.failedAttempts() >= MAX_FAILURES && state.blockedUntil() == null);
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Outcome(
            Authentication authentication,
            boolean invalidCredentials,
            boolean rateLimited) {

        private static Outcome authenticated(Authentication authentication) {
            return new Outcome(authentication, false, false);
        }

        private static Outcome invalidLogin() {
            return new Outcome(null, true, false);
        }

        private static Outcome blocked() {
            return new Outcome(null, false, true);
        }
    }
}
