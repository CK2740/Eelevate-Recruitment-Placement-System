package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.recruitment.dto.LoginRequest;
import com.recruitment.service.AuthenticationService;
import com.recruitment.service.LoginRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

abstract class AbstractLoginRateLimitApiTest {

    @MockitoBean
    protected LoginRateLimitService loginRateLimitService;

    @Autowired
    private AuthenticationService authenticationService;

    @BeforeEach
    void delegateLoginToTheExistingAuthenticationFlow() {
        given(loginRateLimitService.authenticate(any(LoginRequest.class), anyString()))
                .willAnswer(invocation -> authenticationService.authenticate(invocation.getArgument(0)));
    }
}
