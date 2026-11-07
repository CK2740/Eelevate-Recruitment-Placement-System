package com.recruitment.service;

import com.recruitment.dto.LoginRequest;
import com.recruitment.util.LoginPerformanceMetrics;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;

    public AuthenticationService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public Authentication authenticate(LoginRequest request) {
        try {
            long startedAt = LoginPerformanceMetrics.startTimer();
            try {
                return authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                request.email().trim().toLowerCase(Locale.ROOT),
                                request.password()));
            } finally {
                LoginPerformanceMetrics.logDuration("authentication_manager", startedAt);
            }
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password",
                    exception);
        }
    }
}
