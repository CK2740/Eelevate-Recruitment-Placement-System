package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.security.AuditLoggingFilter;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuditLogService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

class AuditLoggingFilterTest {

    private final AuditLogService auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    private final OncePerRequestFilter filter =
            new AuditLoggingFilter(auditLogService, new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamsCsvWithoutCachingResponseBodyOrDisablingExistingReadAuditBehavior() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/reports/OPEN_JOB_AGING/csv");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = new byte[1024 * 1024];

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            assertSame(response, wrappedResponse);
            wrappedResponse.getOutputStream().write(body);
        });

        assertEquals(body.length, response.getContentAsByteArray().length);
        verify(auditLogService, never()).log(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void preservesMutationAuditAndSensitiveFieldRedaction() throws Exception {
        UserPrincipal principal = new UserPrincipal(
                17,
                "admin@example.com",
                "Admin",
                "not-a-real-password",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jobs");
        request.setContentType("application/json");
        request.setContent("""
                {"jobTitle":"Engineer","password":"must-not-be-audited","token":"secret"}
                """.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            assertTrue(wrappedResponse instanceof ContentCachingResponseWrapper);
            wrappedRequest.getInputStream().readAllBytes();
            wrappedResponse.getWriter().write("{\"jobId\":42}");
        });

        assertEquals("{\"jobId\":42}", response.getContentAsString());
        var details = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).log(
                eq(17L),
                eq("JOB_CREATED"),
                eq("JOB"),
                eq(42L),
                details.capture());
        assertEquals(Map.of("jobTitle", "Engineer"), details.getValue().get("fields"));
        assertTrue(!details.getValue().toString().contains("must-not-be-audited"));
        assertTrue(!details.getValue().toString().contains("secret"));
    }
}
