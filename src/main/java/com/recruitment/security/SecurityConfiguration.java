package com.recruitment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.exception.ApiError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LoginPerformancePasswordEncoder(new BCryptPasswordEncoder());
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new TimingDaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Restricts credentialed browser requests to the configured frontend origin.
     *
     * @param allowedOrigin the single origin allowed to call the API
     */
    @Bean
    public static CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") final String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Creates the CSRF cookie with the configured session-cookie security settings.
     *
     * @param secureCookie whether the CSRF cookie must be sent only over HTTPS
     * @param sameSiteCookie the SameSite policy shared with the session cookie
     * @return the configured CSRF token repository
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") final boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site:Lax}") final String sameSiteCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookie)
                .sameSite(sameSiteCookie)
                .path("/"));
        return repository;
    }

    @Bean
    public AuditLoggingFilter auditLoggingFilter(
            com.recruitment.service.AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        return new AuditLoggingFilter(auditLogService, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            AuditLoggingFilter auditLoggingFilter,
            com.recruitment.service.AuditLogService auditLogService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            final CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/auth/register")
                        .permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**")
                        .permitAll()
                        .requestMatchers("/api/auth/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/portal/**").authenticated()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/candidates", "/api/candidates/**").authenticated()
                        .requestMatchers("/api/companies", "/api/companies/**").authenticated()
                        .requestMatchers("/api/jobs", "/api/jobs/**").authenticated()
                        .requestMatchers("/api/applications", "/api/applications/**").authenticated()
                        .requestMatchers("/api/interviews", "/api/interviews/**").authenticated()
                        .requestMatchers("/api/offers", "/api/offers/**").authenticated()
                        .requestMatchers("/api/placements", "/api/placements/**").authenticated()
                        .requestMatchers("/api/dashboard", "/api/dashboard/**").authenticated()
                        .requestMatchers("/api/reports", "/api/reports/**").authenticated()
                        .requestMatchers("/api/audit-logs", "/api/audit-logs/**").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        objectMapper,
                                        request.getRequestURI(),
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(
                                        objectMapper,
                                        request.getRequestURI(),
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "Access is denied")));
        http.addFilterAfter(auditLoggingFilter, AuthorizationFilter.class);
        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    if (authentication != null
                            && authentication.getPrincipal() instanceof UserPrincipal principal) {
                        auditLogService.log(
                                principal.getUserId(),
                                "USER_LOGGED_OUT",
                                "AUTHENTICATION",
                                principal.getUserId(),
                                Map.of("method", "POST", "path", "/api/auth/logout"));
                    }
                    csrfTokenRepository.saveToken(null, request, response);
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                }));
        return http.build();
    }

    private static void writeSecurityError(
            ObjectMapper objectMapper,
            String path,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                Map.of()));
    }
}
