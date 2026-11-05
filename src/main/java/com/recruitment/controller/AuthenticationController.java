package com.recruitment.controller;

import com.recruitment.dto.AuthenticatedUserResponse;
import com.recruitment.dto.CreateUserRequest;
import com.recruitment.dto.CsrfTokenResponse;
import com.recruitment.dto.LoginRequest;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuthenticationService;
import com.recruitment.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserManagementService userManagementService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthenticationController(
            AuthenticationService authenticationService,
            UserManagementService userManagementService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationService = authenticationService;
        this.userManagementService = userManagementService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticationService.authenticate(request);
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Authenticated principal has an unsupported type");
        }

        if (servletRequest.getSession(false) != null) {
            servletRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
        return AuthenticatedUserResponse.from(principal);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return AuthenticatedUserResponse.from(principal);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AuthenticatedUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }
}
