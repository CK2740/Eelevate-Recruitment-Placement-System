package com.recruitment.service;

import com.recruitment.dao.LoginAttemptDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class LoginAttemptCleanup {

    private final LoginAttemptDao loginAttemptDao;

    public LoginAttemptCleanup(LoginAttemptDao loginAttemptDao) {
        this.loginAttemptDao = loginAttemptDao;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void deleteExpiredAttempts() {
        loginAttemptDao.deleteExpiredBatch();
    }
}
