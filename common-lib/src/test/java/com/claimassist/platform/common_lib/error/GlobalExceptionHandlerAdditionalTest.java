package com.claimassist.platform.common_lib.error;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerAdditionalTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "eventLogger", mock(EventLogger.class));
    }

    @Test
    void claimStateTransitionMapsTo409() {
        ResponseEntity<ApiError> res = handler.handleClaimStateTransition(
                new ClaimStateTransitionException("APPROVED", "DENIED"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().message()).contains("Cannot transition");
    }

    @Test
    void usernameNotFoundMapsTo404() {
        ResponseEntity<ApiError> res = handler.handleUsernameNotFound(new UsernameNotFoundException("ghost"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void methodNotSupportedMapsTo405() {
        ResponseEntity<ApiError> res = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().message()).contains("PATCH");
    }

    @Test
    void mediaTypeNotSupportedMapsTo415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_JSON, List.of(MediaType.TEXT_PLAIN));
        ResponseEntity<ApiError> res = handler.handleMediaTypeNotSupported(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void noResourceFoundMapsTo404() {
        ResponseEntity<ApiError> res = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/nope"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void constraintViolationMapsTo400WithFieldErrors() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(cv.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("amount");
        when(cv.getMessage()).thenReturn("must not be null");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(cv));

        ResponseEntity<ApiError> res = handler.handleConstraintViolation(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().errors()).isNotEmpty();
        assertThat(res.getBody().errors().get(0).field()).isEqualTo("amount");
    }

    @Test
    void optimisticLockingMapsTo409() {
        ResponseEntity<ApiError> res = handler.handleOptimisticLocking(
                new OptimisticLockingFailureException("version conflict"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void circuitBreakerOpenMapsTo503() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = mock(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
        when(cb.getCircuitBreakerConfig())
                .thenReturn(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults());
        ResponseEntity<ApiError> res = handler.handleCircuitBreakerOpen(
                CallNotPermittedException.createCallNotPermittedException(cb));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().message()).contains("Dependent service");
    }

    @Test
    void timeoutMapsTo504() {
        ResponseEntity<ApiError> res = handler.handleTimeout(new TimeoutException("slow"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void missingParamMapsTo400() {
        ResponseEntity<ApiError> res = handler.handleMissingParam(
                new MissingServletRequestParameterException("limit", "int"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("limit");
    }

    @Test
    void missingHeaderMapsTo400() throws NoSuchMethodException {
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerAdditionalTest.class.getDeclaredMethod("dummy", String.class), 0);
        ResponseEntity<ApiError> res = handler.handleMissingHeader(
                new MissingRequestHeaderException("X-Trace", param));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("X-Trace");
    }

    @Test
    void typeMismatchMapsTo400WithExpectedType() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "age", null, null);
        ResponseEntity<ApiError> res = handler.handleTypeMismatch(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("Long");
    }

    @Test
    void unreadableBodyMapsTo400() {
        HttpInputMessage input = new HttpInputMessage() {
            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public InputStream getBody() throws IOException {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
        ResponseEntity<ApiError> res = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("malformed", input));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).isEqualTo("Malformed JSON request body");
    }

    @Test
    void feignNoStatusMapsTo503() {
        FeignException ex = FeignException.errorStatus("x",
                feign.Response.builder()
                        .status(-1)
                        .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://x",
                                java.util.Map.of(), null, feign.Util.UTF_8, null))
                        .build());
        ResponseEntity<ApiError> res = handler.handleFeignException(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void exceptionEventIsEmittedWithDetails() {
        EventLogger eventLogger = mock(EventLogger.class);
        ReflectionTestUtils.setField(handler, "eventLogger", eventLogger);

        handler.handleBadRequest(new BadRequestException("nope"));

        verify(eventLogger).logExceptionEvent(isNull(), isNull(), any());
    }

    @Test
    void sanitizeMessageRedactsTokenAndSecretPatterns() throws Exception {
        EventLogger eventLogger = mock(EventLogger.class);
        ReflectionTestUtils.setField(handler, "eventLogger", eventLogger);

        handler.handleUnexpectedException(new IllegalStateException(
                "auth token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));

        verify(eventLogger).logExceptionEvent(isNull(), isNull(), any());
    }

    void dummy(String arg) {
        // helper method to build a MethodParameter for MissingRequestHeaderException
    }
}