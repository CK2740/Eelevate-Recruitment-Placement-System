package com.recruitment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.exception.ApiError;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
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

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/"));
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
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
        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
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
