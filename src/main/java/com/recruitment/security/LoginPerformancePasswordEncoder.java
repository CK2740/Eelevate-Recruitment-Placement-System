package com.recruitment.security;

import com.recruitment.util.LoginPerformanceMetrics;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class LoginPerformancePasswordEncoder implements PasswordEncoder {

    /** Encoder that performs the original password operations. */
    private final PasswordEncoder delegate;

    /**
     * Creates a timing wrapper around the supplied encoder.
     *
     * @param passwordEncoder encoder to wrap
     */
    public LoginPerformancePasswordEncoder(
            final PasswordEncoder passwordEncoder) {
        this.delegate = passwordEncoder;
    }

    @Override
    public String encode(final CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(
            final CharSequence rawPassword,
            final String encodedPassword) {
        long startedAt = LoginPerformanceMetrics.startTimer();
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } finally {
            LoginPerformanceMetrics.logDuration(
                    "bcrypt_password_match",
                    startedAt);
        }
    }

    @Override
    public boolean upgradeEncoding(final String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
