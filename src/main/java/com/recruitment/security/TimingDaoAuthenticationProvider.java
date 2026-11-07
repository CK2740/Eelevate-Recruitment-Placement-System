package com.recruitment.security;

import com.recruitment.util.LoginPerformanceMetrics;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;

public final class TimingDaoAuthenticationProvider extends DaoAuthenticationProvider {

    public TimingDaoAuthenticationProvider(UserDetailsService userDetailsService) {
        super(userDetailsService);
        setPreAuthenticationChecks(timedChecker(
                getPreAuthenticationChecks(), "provider_pre_authentication_checks"));
        setPostAuthenticationChecks(timedChecker(
                getPostAuthenticationChecks(), "provider_post_authentication_checks"));
    }

    @Override
    protected Authentication createSuccessAuthentication(
            Object principal,
            Authentication authentication,
            UserDetails user) {
        long startedAt = LoginPerformanceMetrics.startTimer();
        try {
            return super.createSuccessAuthentication(principal, authentication, user);
        } finally {
            LoginPerformanceMetrics.logDuration(
                    "provider_success_authentication", startedAt);
        }
    }

    private static UserDetailsChecker timedChecker(
            UserDetailsChecker delegate,
            String operation) {
        return user -> {
            long startedAt = LoginPerformanceMetrics.startTimer();
            try {
                delegate.check(user);
            } finally {
                LoginPerformanceMetrics.logDuration(operation, startedAt);
            }
        };
    }
}
