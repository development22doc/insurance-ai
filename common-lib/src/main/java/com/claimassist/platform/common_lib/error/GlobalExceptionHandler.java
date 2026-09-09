package com.claimassist.platform.common_lib.error;

import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Every service in the platform picks this up automatically via
 * SharedExceptionAutoConfiguration - do not duplicate an exception handler
 * in an individual service; add the case here instead so all services stay
 * consistent. NOTE: this only covers Servlet/MVC apps. api-gateway is
 * WebFlux-based and is NOT on the classpath for this class - see
 * GatewayExceptionHandler in api-gateway for its reactive equivalent.
 *
 * Registration happens via SharedExceptionAutoConfiguration.globalExceptionHandler()
 * Bean method to prevent duplicate bean definitions in services that override it.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private EventLogger eventLogger;

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage());
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "BAD_REQUEST", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        ApiError apiError = new ApiError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "SERVICE_UNAVAILABLE", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ClaimStateTransitionException.class)
    public ResponseEntity<ApiError> handleClaimStateTransition(ClaimStateTransitionException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT, ex.getMessage());
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "STATE_CONFLICT", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "RESOURCE_NOT_FOUND", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> handleUsernameNotFound(UsernameNotFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "USERNAME_NOT_FOUND", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        ApiError apiError = new ApiError(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "AUTHENTICATION_FAILED", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "VALIDATION_FAILED", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex) {
        ApiError apiError = new ApiError(HttpStatus.FORBIDDEN, "Access denied: Insufficient permissions");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "ACCESS_DENIED", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Malformed / unparsable requests (400) ----------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "MALFORMED_REQUEST", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "a different type";
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Parameter '%s' must be of type %s".formatted(ex.getName(), expected));
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "TYPE_MISMATCH", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()));
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "MISSING_PARAMETER", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Required header '%s' is missing".formatted(ex.getHeaderName()));
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "MISSING_HEADER", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /** Bean-validation on @RequestParam/@PathVariable (requires @Validated on the controller). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> new ApiFieldError(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "CONSTRAINT_VIOLATION", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Routing / protocol mismatches -------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ApiError apiError = new ApiError(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod()));
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "METHOD_NOT_ALLOWED", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        ApiError apiError = new ApiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '%s' is not supported".formatted(ex.getContentType()));
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "UNSUPPORTED_MEDIA_TYPE", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, "No endpoint matches this request");
        log.warn(apiError.toString());
        emitExceptionEvent(ex, "NO_RESOURCE_FOUND", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Persistence layer --------------------------------------------------

    /** Unique/FK/NOT NULL constraint violations bubbling up from the DB driver. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT, "The request conflicts with existing data");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "DATA_INTEGRITY_VIOLATION", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /** Optimistic locking conflicts (concurrent update to the same row). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT,
                "This record was updated by another request - please retry with the latest version");
        log.warn(apiError.toString(), ex);
        emitExceptionEvent(ex, "OPTIMISTIC_LOCK_FAILURE", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Downstream / inter-service call failures ----------------------------

    /** Propagated status from a Feign call to another internal service (claims/customer). */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeignException(FeignException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        // status is null when Feign never got a response at all (e.g. status() == -1 on connect failure)
        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }
        ApiError apiError = new ApiError(status, "Upstream service call failed");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "FEIGN_EXCEPTION", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /** Resilience4j circuit breaker is OPEN - fail fast instead of hammering a struggling dependency. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        ApiError apiError = new ApiError(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependent service is temporarily unavailable - please retry shortly");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "CIRCUIT_BREAKER_OPEN", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiError> handleTimeout(TimeoutException ex) {
        ApiError apiError = new ApiError(HttpStatus.GATEWAY_TIMEOUT, "Dependent service call timed out");
        log.error(apiError.toString(), ex);
        emitExceptionEvent(ex, "TIMEOUT", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /**
     * Catch-all safety net - see PRODUCTION_READINESS.md in the Lovable-clone
     * project for the full rationale. Every unmapped exception still returns
     * this app's consistent ApiError shape, never leaking internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex) {
        ApiError apiError = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        log.error("Unhandled exception: {}", apiError, ex);
        emitExceptionEvent(ex, "UNHANDLED_EXCEPTION", apiError.status());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /**
     * Emits a structured exception event via EventLogger if available.
     */
    private void emitExceptionEvent(Exception ex, String errorCode, HttpStatus status) {
        if (eventLogger == null) {
            return;
        }

        Map<String, Object> details = new HashMap<>();
        details.put("errorCode", errorCode);
        details.put("exceptionType", ex.getClass().getSimpleName());
        details.put("message", sanitizeMessage(ex.getMessage()));
        details.put("httpStatus", status.value());
        details.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
        details.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
        details.put("spanId", MDC.get(LoggingConstants.MDC_SPAN_ID));

        try {
            eventLogger.logExceptionEvent(null, null, details);
        } catch (Exception e) {
            // Log emission failure silently to avoid cascading errors
            log.debug("Failed to emit exception event", e);
        }
    }

    /**
     * Sanitizes exception messages to prevent logging of sensitive information.
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String sanitized = message;

        // Remove JWT patterns
        sanitized = sanitized.replaceAll("(?i)(bearer\\s+)?[A-Za-z0-9-_]{20,}", "***JWT_REDACTED***");

        // Remove authorization/password/token/secret patterns, including secret-value and token-value forms.
        // For cases like "secret-****** at com.acme.internal.Thing" the key name and internal package path are also stripped.
        sanitized = sanitized.replaceAll("(?i)\\b(?:authorization|password|token|refresh|secret|api[_-]?key)\\s*[:=\\-]\\s*[^\\s,;]+", "***REDACTED***");
        sanitized = sanitized.replaceAll("(?i)\\b(?:authorization|password|token|refresh|secret|api[_-]?key)\\s+[^\\s,;]+", "***REDACTED***");

        // Remove internal package/class markers from stack traces and similar strings.
        sanitized = sanitized.replaceAll("(?i)\\b(?:com|org|net|io)\\.[A-Za-z0-9_.]+", "[REDACTED_PACKAGE]");
        sanitized = sanitized.replaceAll("(?i)\\bat\\s+[A-Za-z0-9_$.]+(?:\\.[A-Za-z0-9_$.]+)+", "at [REDACTED_LOCATION]");

        return sanitized;
    }
}
