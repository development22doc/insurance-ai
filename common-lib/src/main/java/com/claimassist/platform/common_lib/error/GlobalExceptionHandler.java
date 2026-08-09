package com.claimassist.platform.common_lib.error;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Every service in the platform picks this up automatically via
 * SharedExceptionAutoConfiguration - do not duplicate an @RestControllerAdvice
 * in an individual service; add the case here instead so all services stay
 * consistent. NOTE: this only covers Servlet/MVC apps. api-gateway is
 * WebFlux-based and is NOT on the classpath for this class - see
 * GatewayExceptionHandler in api-gateway for its reactive equivalent.
 *
 * @RestControllerAdvice annotation removed - registration happens via
 * SharedExceptionAutoConfiguration.globalExceptionHandler() @Bean method
 * to prevent duplicate bean definitions in services that override it.
 */
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage());
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        ApiError apiError = new ApiError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ClaimStateTransitionException.class)
    public ResponseEntity<ApiError> handleClaimStateTransition(ClaimStateTransitionException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT, ex.getMessage());
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> handleUsernameNotFound(UsernameNotFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        ApiError apiError = new ApiError(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex) {
        ApiError apiError = new ApiError(HttpStatus.FORBIDDEN, "Access denied: Insufficient permissions");
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Malformed / unparsable requests (400) ----------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "a different type";
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Parameter '%s' must be of type %s".formatted(ex.getName(), expected));
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()));
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        ApiError apiError = new ApiError(HttpStatus.BAD_REQUEST,
                "Required header '%s' is missing".formatted(ex.getHeaderName()));
        log.warn(apiError.toString(), ex);
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
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Routing / protocol mismatches -------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ApiError apiError = new ApiError(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod()));
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        ApiError apiError = new ApiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '%s' is not supported".formatted(ex.getContentType()));
        log.warn(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        ApiError apiError = new ApiError(HttpStatus.NOT_FOUND, "No endpoint matches this request");
        log.warn(apiError.toString());
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    // ---- Persistence layer --------------------------------------------------

    /** Unique/FK/NOT NULL constraint violations bubbling up from the DB driver. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT, "The request conflicts with existing data");
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /** @Version-based optimistic locking conflicts (concurrent update to the same row). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT,
                "This record was updated by another request - please retry with the latest version");
        log.warn(apiError.toString(), ex);
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
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    /** Resilience4j circuit breaker is OPEN - fail fast instead of hammering a struggling dependency. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        ApiError apiError = new ApiError(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependent service is temporarily unavailable - please retry shortly");
        log.error(apiError.toString(), ex);
        return ResponseEntity.status(apiError.status()).body(apiError);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiError> handleTimeout(TimeoutException ex) {
        ApiError apiError = new ApiError(HttpStatus.GATEWAY_TIMEOUT, "Dependent service call timed out");
        log.error(apiError.toString(), ex);
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
        return ResponseEntity.status(apiError.status()).body(apiError);
    }
}
