package com.recruitment.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class AuditLoggingFilter extends OncePerRequestFilter {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLoggingFilter(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 64 * 1024);
        if (isReadOnlyMethod(request.getMethod())) {
            filterChain.doFilter(requestWrapper, response);
            return;
        }
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
            if (responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300) {
                auditMutation(requestWrapper, responseWrapper);
            }
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    private static boolean isReadOnlyMethod(final String method) {
        return "GET".equals(method) || "HEAD".equals(method)
                || "OPTIONS".equals(method);
    }

    private void auditMutation(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response) throws IOException {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        String path = request.getRequestURI();
        if (path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }
        if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")) {
            return;
        }
        String entity = entityFor(path);
        if (entity == null) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", method);
        details.put("path", path);
        details.put("fields", auditedFields(request));
        auditLogService.log(
                principal.getUserId(),
                actionFor(entity, method, path),
                entity,
                entityId(entity, path, response),
                details);
    }

    private Map<String, Object> auditedFields(ContentCachingRequestWrapper request) throws IOException {
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return Map.of();
        }
        JsonNode node = objectMapper.readTree(body);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : new String[] {
                "status", "joiningStatus", "scheduledAt", "interviewRound",
                "designation", "technology", "jobTitle", "companyName",
                "packageAmount", "joiningDate", "expiryDate", "offerDate"
        }) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                fields.put(field, value.isValueNode() ? value.asText() : value.toString());
            }
        }
        return fields;
    }

    private Long entityId(
            String entity,
            String path,
            ContentCachingResponseWrapper response) throws IOException {
        String idField = entity.toLowerCase(Locale.ROOT) + "Id";
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        if (body != null && body.isObject() && body.hasNonNull(idField)) {
            return body.get(idField).asLong();
        }
        String plural = switch (entity) {
            case "CANDIDATE" -> "candidates";
            case "COMPANY" -> "companies";
            case "JOB" -> "jobs";
            case "APPLICATION" -> "applications";
            case "INTERVIEW" -> "interviews";
            case "OFFER" -> "offers";
            case "PLACEMENT" -> "placements";
            default -> "";
        };
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (plural.equals(parts[i])) {
                try {
                    return Long.valueOf(parts[i + 1]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String entityFor(String path) {
        if (path.contains("/candidates")) {
            return "CANDIDATE";
        }
        if (path.contains("/companies")) {
            return "COMPANY";
        }
        if (path.contains("/interviews")) {
            return "INTERVIEW";
        }
        if (path.contains("/offers")) {
            return "OFFER";
        }
        if (path.contains("/placements")) {
            return "PLACEMENT";
        }
        if (path.contains("/applications")) {
            return "APPLICATION";
        }
        if (path.contains("/jobs")) {
            return "JOB";
        }
        return null;
    }

    private static String actionFor(String entity, String method, String path) {
        String suffix = switch (method) {
            case "POST" -> entity.equals("INTERVIEW") ? "SCHEDULED" : "CREATED";
            case "PUT" -> "UPDATED";
            case "PATCH" -> "STATUS_CHANGED";
            case "DELETE" -> "DELETED";
            default -> "CHANGED";
        };
        if (path.endsWith("/feedback")) {
            suffix = "FEEDBACK_SUBMITTED";
        } else if (path.endsWith("/status")) {
            suffix = "STATUS_CHANGED";
        } else if (path.endsWith("/schedule")) {
            suffix = "RESCHEDULED";
        } else if (path.endsWith("/joining-date")) {
            suffix = "JOINING_DATE_UPDATED";
        }
        return entity + "_" + suffix;
    }
}
