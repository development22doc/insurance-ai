package com.claimassist.platform.customer_service.exception;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
}