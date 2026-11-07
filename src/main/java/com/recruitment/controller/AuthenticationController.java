package com.recruitment.controller;

import com.recruitment.dto.AuthenticatedUserResponse;
import com.recruitment.dto.CreateUserRequest;
import com.recruitment.dto.CsrfTokenResponse;
import com.recruitment.dto.LoginRequest;
import com.recruitment.dto.PublicRegistrationRequest;
import com.recruitment.dto.RegistrationResponse;
import com.recruitment.dto.RecruiterApprovalRequest;
import com.recruitment.dto.RecruiterRegistrationResponse;
import com.recruitment.dto.UpdateUserRoleRequest;
import com.recruitment.dto.UserManagementPageResponse;
import com.recruitment.dto.UserManagementResponse;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuthenticationService;
import com.recruitment.service.AuditLogService;
import com.recruitment.service.LoginRateLimitService;
import com.recruitment.service.UserManagementService;
import com.recruitment.util.LoginPerformanceMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final LoginRateLimitService loginRateLimitService;
    private final AuditLogService auditLogService;
    private final UserManagementService userManagementService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthenticationController(
            AuthenticationService authenticationService,
            final LoginRateLimitService rateLimitService,
            AuditLogService auditLogService,
            UserManagementService userManagementService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationService = authenticationService;
        this.loginRateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
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
        long startedAt = LoginPerformanceMetrics.startTimer();
        try {
            Authentication authentication = loginRateLimitService.authenticate(
                    request, servletRequest.getRemoteAddr());
            return establishSession(authentication, servletRequest, servletResponse, "/api/auth/login");
        } finally {
            LoginPerformanceMetrics.logDuration("controller_total", startedAt);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody PublicRegistrationRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        userManagementService.registerPublic(request);
        if (request.accountType() == com.recruitment.dto.PublicAccountType.RECRUITER) {
            return ResponseEntity.accepted().body(new RegistrationResponse(
                    "Your recruiter account is awaiting Admin approval.",
                    null));
        }
        Authentication authentication = authenticationService.authenticate(
                new LoginRequest(request.email(), request.password()));
        AuthenticatedUserResponse authenticated = establishSession(
                authentication, servletRequest, servletResponse, "/api/auth/register");
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(
                "Candidate account created.",
                authenticated));
    }

    private AuthenticatedUserResponse establishSession(
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            String path) {
        UserPrincipal principal = requireUserPrincipal(authentication);
        auditLogService.log(
                principal.getUserId(),
                "USER_LOGGED_IN",
                "AUTHENTICATION",
                principal.getUserId(),
                Map.of("method", "POST", "path", path));

        long startedAt = LoginPerformanceMetrics.startTimer();
        try {
            if (servletRequest.getSession(false) != null) {
                servletRequest.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            long saveStartedAt = LoginPerformanceMetrics.startTimer();
            try {
                securityContextRepository.saveContext(context, servletRequest, servletResponse);
            } finally {
                LoginPerformanceMetrics.logDuration("security_context_save", saveStartedAt);
            }
            csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
            return AuthenticatedUserResponse.from(principal);
        } finally {
            LoginPerformanceMetrics.logDuration("session_establishment", startedAt);
        }
    }

    private static UserPrincipal requireUserPrincipal(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Authenticated principal has an unsupported type");
        }
        return principal;
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return AuthenticatedUserResponse.from(principal);
    }

    @GetMapping("/admin/recruiters/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RecruiterRegistrationResponse> getPendingRecruiters() {
        return userManagementService.getPendingRecruiters();
    }

    @PostMapping("/admin/recruiters/{recruiterId}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> reviewRecruiter(
            @org.springframework.web.bind.annotation.PathVariable long recruiterId,
            @Valid @RequestBody RecruiterApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userManagementService.reviewRecruiter(recruiterId, principal.getUserId(), request.decision());
        String decision = request.decision() == RecruiterApprovalRequest.Decision.APPROVE
                ? "approved"
                : "rejected";
        return Map.of("message", "Recruiter registration " + decision + ".");
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AuthenticatedUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public UserManagementPageResponse getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userManagementService.getUsers(page, size);
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserManagementResponse updateUserRole(
            @PathVariable @Min(1) long userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return userManagementService.updateUserRole(userId, request, principal.getUserId());
    }
}
