package com.claimassist.platform.customer_service.exception;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 2: the global exception handler must return structured, sanitized
 * errors with correct HTTP statuses. In particular, @Valid request-body
 * failures must be a 400 (not a 500), and error bodies must never leak
 * internal exception details to the client.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        EventLogger eventLogger = mock(EventLogger.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/policies");
        when(request.getMethod()).thenReturn("POST");
        handler = new GlobalExceptionHandler(eventLogger, currentUserProvider, request);
    }

    @Test
    void badRequestReturns400StructuredError() {
        ResponseEntity<EnhancedApiError> response = handler.handleBadRequest(new BadRequestException("bad input"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("bad input");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/policies");
    }

    @Test
    void validationFailureReturns400Not500() throws Exception {
        BindingResult binding = mock(BindingResult.class);
        when(binding.getFieldErrors()).thenReturn(java.util.List.of(
                new FieldError("obj", "deductible", "must not be null")));
        java.lang.reflect.Method method =
                GlobalExceptionHandler.class.getMethod("handleBadRequest", com.claimassist.platform.common_lib.error.BadRequestException.class);
        org.springframework.core.MethodParameter parameter =
                new org.springframework.core.MethodParameter(method, 0);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<EnhancedApiError> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("deductible");
    }

    @Test
    void accessDeniedReturns403StructuredError() {
        ResponseEntity<EnhancedApiError> response = handler.handleAccessDeniedException(new AccessDeniedException("nope"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().errorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void unexpectedExceptionIs500AndNeverLeaksInternals() {
        ResponseEntity<EnhancedApiError> response =
                handler.handleUnexpectedException(new RuntimeException("secret: jdbc:postgresql://db:5432 conn failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message())
                .doesNotContain("secret").doesNotContain("jdbc").doesNotContain("RuntimeException");
    }

    @Test
    void constraintViolationReturns400ValidationError() {
        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("deductible");
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn("must not be null");
        ConstraintViolationException ex =
                new ConstraintViolationException("validation failed", Set.of(cv));

        ResponseEntity<EnhancedApiError> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("deductible");
    }

    @Test
    void resourceNotFoundReturns404() {
        ResponseEntity<EnhancedApiError> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("claim", "7"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("claim not found with id: 7");
    }

    @Test
    void serviceUnavailableReturns503() {
        ResponseEntity<EnhancedApiError> response =
                handler.handleServiceUnavailable(new ServiceUnavailableException("downstream down"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().errorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("downstream down");
    }

    @Test
    void claimStateTransitionReturns409() {
        ResponseEntity<EnhancedApiError> response =
                handler.handleClaimStateTransition(new ClaimStateTransitionException("DENIED", "APPROVED"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo("STATE_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Cannot transition claim from DENIED to APPROVED");
    }

    @Test
    void authenticationFailureReturns401() {
        ResponseEntity<EnhancedApiError> response =
                handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().errorCode()).isEqualTo("AUTHENTICATION_FAILED");
    }

    @Test
    void missingCredentialsReturns401() {
        ResponseEntity<EnhancedApiError> response = handler.handleAuthenticationCredentialsNotFound(
                new AuthenticationCredentialsNotFoundException("no credentials"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().errorCode()).isEqualTo("MISSING_CREDENTIALS");
    }

    @Test
    void methodNotAllowedReturns405() {
        ResponseEntity<EnhancedApiError> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void errorBodyCarriesMethodAndPath() {
        ResponseEntity<EnhancedApiError> response = handler.handleBadRequest(new BadRequestException("x"));
        assertThat(response.getBody().method()).isEqualTo("POST");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/policies");
    }

    @Test
    void capturedExceptionEventIsSanitizedOfSecrets() {
        Map<String, Object>[] captured = new Map[1];
        EventLogger capturingLogger = new EventLogger() {
            @Override public void logRequestEvent(String s, String a, long ms, Map<String, Object> d) { }
            @Override public void logBusinessEvent(String s, String a, Map<String, Object> d) { }
            @Override public void logSecurityEvent(String s, String a, Map<String, Object> d) { }
            @Override public void logDatabaseEvent(String s, String a, long ms, Map<String, Object> d) { }
            @Override public void logKafkaEvent(String s, String a, Map<String, Object> d) { }
            @Override public void logPerformanceEvent(String s, String a, long ms, Map<String, Object> d) { }
            @Override public void logExceptionEvent(String s, String a, Map<String, Object> d) { captured[0] = d; }
        };
        GlobalExceptionHandler recorder = new GlobalExceptionHandler(capturingLogger,
                mock(CurrentUserProvider.class), request);

        recorder.handleUnexpectedException(new RuntimeException(
                "login failed password=hunter2 token=abc123 secret=xyz api_key=k123 "
                        + "authorization=Bearer eyJhbGciOiJIUzI1NiJ9.some.payload.more"));

        Map<String, Object> details = captured[0];
        assertThat(details).isNotNull();
        String message = String.valueOf(details.get("message"));
        assertThat(message)
                .doesNotContain("hunter2")
                .doesNotContain("abc123")
                .doesNotContain("xyz")
                .doesNotContain("k123")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .contains("***");
    }

    @Test
    void accessDeniedWithFailingUserProviderStillReturns403() {
        CurrentUserProvider failingProvider = mock(CurrentUserProvider.class);
        when(failingProvider.getCurrentUsername()).thenThrow(new IllegalStateException("unauthenticated"));
        GlobalExceptionHandler local = new GlobalExceptionHandler(mock(EventLogger.class), failingProvider, request);

        ResponseEntity<EnhancedApiError> response =
                local.handleAccessDeniedException(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().errorCode()).isEqualTo("ACCESS_DENIED");
    }
}