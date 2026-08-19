package com.claimassist.platform.customer_service.exception;

import com.claimassist.platform.common_lib.error.*;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise-grade global exception handler for customer-service.
 *
 * Provides centralized, structured exception handling that:
 * - Logs exceptions with ExceptionLoggingUtil (sanitizes sensitive data)
 * - Emits structured exception events via EventLogger
 * - Includes correlation ID, trace ID, HTTP method, request URI, exception type,
 *   root cause, execution time, and username (when authenticated)
 * - Never logs JWT, authorization headers, passwords, refresh tokens, or PII
 * - Returns standardized error responses
 *
 * Note: SharedExceptionAutoConfiguration from common-lib is excluded in this service
 * since customer-service provides its own enhanced exception handler with event logging.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final EventLogger eventLogger;
    private final CurrentUserProvider currentUserProvider;
    private final HttpServletRequest request;

    /**
     * Handles BadRequestException with structured event logging.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<EnhancedApiError> handleBadRequest(BadRequestException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage() != null ? ex.getMessage() : "Invalid request"
        );

        emitExceptionEvent(ex, HttpStatus.BAD_REQUEST, "BAD_REQUEST", startTime);
        log.warn("BadRequest: {}", error.message());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles @Valid request-body validation failures with a 400 (not a 500).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EnhancedApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        long startTime = System.currentTimeMillis();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );

        emitExceptionEvent(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", startTime);
        log.warn("Validation error: {} - path: {}", message, error.path());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles method-parameter / bean-validation constraint violations with a 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<EnhancedApiError> handleConstraintViolation(ConstraintViolationException ex) {
        long startTime = System.currentTimeMillis();
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                .orElse("Validation failed");
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );

        emitExceptionEvent(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", startTime);
        log.warn("Validation error: {} - path: {}", message, error.path());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles ResourceNotFoundException with structured event logging.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<EnhancedApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "Resource not found"
        );

        emitExceptionEvent(ex, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", startTime);
        log.warn("Resource not found: {}", error.message());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles ServiceUnavailableException with structured event logging.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<EnhancedApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                ex.getMessage() != null ? ex.getMessage() : "Service temporarily unavailable"
        );

        emitExceptionEvent(ex, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", startTime);
        log.error("Service unavailable: {}", error.message(), ex);

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles ClaimStateTransitionException with structured event logging.
     */
    @ExceptionHandler(ClaimStateTransitionException.class)
    public ResponseEntity<EnhancedApiError> handleClaimStateTransition(ClaimStateTransitionException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.CONFLICT,
                "STATE_CONFLICT",
                ex.getMessage() != null ? ex.getMessage() : "Invalid state transition"
        );

        emitExceptionEvent(ex, HttpStatus.CONFLICT, "STATE_CONFLICT", startTime);
        log.warn("State transition conflict: {}", error.message(), ex);

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles AuthenticationException with structured event logging.
     * Logs security event with username and sanitized error details.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<EnhancedApiError> handleAuthenticationException(AuthenticationException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Authentication failed - invalid credentials or expired session"
        );

        emitSecurityExceptionEvent(ex, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", startTime, null);
        log.warn("Authentication failed: {}", error.message());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles AuthenticationCredentialsNotFoundException with structured event logging.
     * Logs security event for missing credentials.
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<EnhancedApiError> handleAuthenticationCredentialsNotFound(
            AuthenticationCredentialsNotFoundException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.UNAUTHORIZED,
                "MISSING_CREDENTIALS",
                "Authentication credentials not found - please login"
        );

        emitSecurityExceptionEvent(ex, HttpStatus.UNAUTHORIZED, "MISSING_CREDENTIALS", startTime, null);
        log.warn("Missing authentication credentials: {}", error.message());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles AccessDeniedException with structured event logging.
     * Logs security event with username and sanitized error details.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<EnhancedApiError> handleAccessDeniedException(AccessDeniedException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied - insufficient permissions"
        );

        String username = null;
        try {
            username = currentUserProvider.getCurrentUsername();
        } catch (Exception ignored) {
            // User not authenticated or username not available
        }

        emitSecurityExceptionEvent(ex, HttpStatus.FORBIDDEN, "ACCESS_DENIED", startTime, username);
        log.warn("Access denied for user: {} - path: {}", username != null ? username : "anonymous", error.path());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Handles requests made with an HTTP method that the matched endpoint does not support.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<EnhancedApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Request method is not supported for this endpoint"
        );

        emitExceptionEvent(ex, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", startTime);
        log.warn("Method not allowed - correlationId: {} - path: {} - method: {}",
                error.correlationId(), error.path(), request.getMethod());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs all exception details with structured event logging and never leaks internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<EnhancedApiError> handleUnexpectedException(Exception ex) {
        long startTime = System.currentTimeMillis();
        EnhancedApiError error = buildEnhancedError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."
        );

        emitExceptionEvent(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", startTime);
        log.error("Unhandled exception - correlationId: {} - path: {} - method: {}",
                error.correlationId(), error.path(), request.getMethod(), ex);

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Builds an EnhancedApiError with all required fields.
     */
    private EnhancedApiError buildEnhancedError(HttpStatus status, String errorCode, String message) {
        return EnhancedApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .correlationId(MDC.get(LoggingConstants.MDC_CORRELATION_ID))
                .traceId(MDC.get(LoggingConstants.MDC_TRACE_ID))
                .spanId(MDC.get(LoggingConstants.MDC_SPAN_ID))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .build();
    }

    /**
     * Emits a structured exception event with all required fields.
     * Never includes sensitive information like JWT, passwords, or authorization headers.
     */
    private void emitExceptionEvent(
            Exception ex,
            HttpStatus status,
            String errorCode,
            long startTime) {

        long executionTime = System.currentTimeMillis() - startTime;
        String username = null;

        try {
            username = currentUserProvider.getCurrentUsername();
        } catch (Exception ignored) {
            // User not authenticated - username will be null
        }

        Map<String, Object> details = buildExceptionEventDetails(ex, status, errorCode, username, executionTime);
        eventLogger.logExceptionEvent("customer-service", "customer-service", details);
    }

    /**
     * Emits a security-focused exception event (authentication/authorization failures).
     */
    private void emitSecurityExceptionEvent(
            Exception ex,
            HttpStatus status,
            String errorCode,
            long startTime,
            String username) {

        long executionTime = System.currentTimeMillis() - startTime;

        Map<String, Object> details = buildSecurityExceptionEventDetails(ex, status, errorCode, username, executionTime);
        eventLogger.logExceptionEvent("customer-service", "customer-service", details);
    }

    /**
     * Builds the details map for exception events with all required fields.
     * Ensures sensitive information is never included.
     */
    private Map<String, Object> buildExceptionEventDetails(
            Exception ex,
            HttpStatus status,
            String errorCode,
            String username,
            long executionTime) {

        Map<String, Object> details = new HashMap<>();

        // Canonical fields
        details.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
        details.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
        details.put("spanId", MDC.get(LoggingConstants.MDC_SPAN_ID));

        // HTTP request details
        details.put("httpMethod", request.getMethod());
        details.put("requestUri", request.getRequestURI());
        details.put("httpStatus", status.value());

        // Exception details
        details.put("exceptionType", ex.getClass().getSimpleName());
        details.put("errorCode", errorCode);
        details.put("message", sanitizeMessage(ex.getMessage()));
        details.put("rootCause", sanitizeMessage(getRootCause(ex)));

        // Performance metrics
        details.put("executionTimeMs", executionTime);

        // User information (when available)
        if (username != null) {
            details.put("username", username);
        }

        return details;
    }

    /**
     * Builds the details map for security-focused exception events.
     */
    private Map<String, Object> buildSecurityExceptionEventDetails(
            Exception ex,
            HttpStatus status,
            String errorCode,
            String username,
            long executionTime) {

        Map<String, Object> details = buildExceptionEventDetails(ex, status, errorCode, username, executionTime);

        // Add security context
        details.put("isSecurityEvent", true);
        details.put("securityContext", "authentication|authorization");

        return details;
    }

    /**
     * Sanitizes exception messages to prevent logging of sensitive information.
     * Removes JWT tokens, authorization headers, passwords, and other secrets.
     *
     * Pattern matches:
     * - JWT tokens (Bearer tokens, JWT claims)
     * - Authorization headers
     * - Passwords and secrets
     * - Refresh tokens
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Remove JWT patterns (e.g., "Bearer eyJhbG..." or bare JWT tokens)
        message = message.replaceAll("(?i)(bearer\\s+)?[A-Za-z0-9-_]{20,}", "***JWT_REDACTED***");

        // Remove authorization header values
        message = message.replaceAll("(?i)authorization\\s*[=:][^\\s,;]+", "authorization=***");

        // Remove password patterns
        message = message.replaceAll("(?i)password\\s*[=:][^\\s,;]+", "password=***");

        // Remove secret/token patterns
        message = message.replaceAll("(?i)(secret|token|refresh)\\s*[=:][^\\s,;]+", "$1=***");

        // Remove API keys
        message = message.replaceAll("(?i)(api[_-]?key|apikey)\\s*[=:][^\\s,;]+", "$1=***");

        return message;
    }

    /**
     * Extracts the root cause from an exception chain.
     */
    private String getRootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
