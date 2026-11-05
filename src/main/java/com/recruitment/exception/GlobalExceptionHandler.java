package com.recruitment.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage(),
                        (firstMessage, ignored) -> firstMessage,
                        LinkedHashMap::new));
        return createError(HttpStatus.BAD_REQUEST, "Request validation failed", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return createError(HttpStatus.BAD_REQUEST, "Request body is invalid", request, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        HttpStatus resolvedStatus = HttpStatus.resolve(status.value());
        String error = resolvedStatus == null ? "Request failed" : resolvedStatus.getReasonPhrase();
        String message = exception.getReason() == null ? error : exception.getReason();
        return createError(status, error, message, request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request) {
        return createError(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unhandled error processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return createError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                request,
                Map.of());
    }

    private ResponseEntity<ApiError> createError(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> details) {
        return createError(status, status.getReasonPhrase(), message, request, details);
    }

    private ResponseEntity<ApiError> createError(
            HttpStatusCode status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> details) {
        ApiError response = new ApiError(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                details);
        return ResponseEntity.status(status).body(response);
    }
}
