package com.claimassist.platform.common_lib.error;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ApiError body(ResponseEntity<ApiError> response) {
        return response.getBody();
    }

    @Test
    void badRequestMapsTo400() {
        ResponseEntity<ApiError> res = handler.handleBadRequest(new BadRequestException("bad"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).message()).isEqualTo("bad");
    }

    @Test
    void serviceUnavailableMapsTo503() {
        ResponseEntity<ApiError> res = handler.handleServiceUnavailable(new ServiceUnavailableException("down"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void resourceNotFoundMapsTo404() {
        ResponseEntity<ApiError> res = handler.handleResourceNotFound(new ResourceNotFoundException("Policy", "2"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void authenticationMapsTo401WithSafeMessage() {
        ResponseEntity<ApiError> res = handler.handleAuthenticationException(new BadCredentialsException("bad creds"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(res).message()).isEqualTo("Invalid credentials");
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ApiError> res = handler.handleAccessDeniedException(new AccessDeniedException("no"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dataIntegrityViolationMapsTo409WithSafeMessage() {
        ResponseEntity<ApiError> res = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key violates unique constraint"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(res).message()).isEqualTo("The request conflicts with existing data");
    }

    @Test
    void feign5xxMapsTo503() {
        FeignException ex = FeignException.errorStatus("getPolicyCoverage",
                feign.Response.builder()
                        .status(503)
                        .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://x", java.util.Map.of(), null, feign.Util.UTF_8, null))
                        .body("down", feign.Util.UTF_8)
                        .build());
        ResponseEntity<ApiError> res = handler.handleFeignException(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(res).message()).isEqualTo("Upstream service call failed");
    }

    @Test
    void feignClientErrorPreservesStatus() {
        feign.Response response = feign.Response.builder()
                .status(404)
                .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://x", java.util.Map.of(), null, feign.Util.UTF_8, null))
                .body("nope", feign.Util.UTF_8)
                .build();
        FeignException ex = FeignException.errorStatus("getPolicyCoverage", response);
        ResponseEntity<ApiError> res = handler.handleFeignException(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void catchAllDoesNotLeakInternals() {
        Exception secret = new IllegalStateException("secret-password=abc123 at com.acme.internal.Thing");
        ResponseEntity<ApiError> res = handler.handleUnexpectedException(secret);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(res).message()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(body(res).message()).doesNotContain("secret", "password", "com.acme");
    }

    @Test
    void apiErrorCarriesStatusAndMessageShape() {
        ApiError err = body(handler.handleBadRequest(new BadRequestException("x")));
        assertThat(err.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(err.timestamp()).isNotNull();
        assertThat(err.errors()).isNull();
    }

    @Test
    void validationErrorCarriesFieldErrors() {
        org.springframework.validation.BindingResult binding = org.mockito.Mockito.mock(org.springframework.validation.BindingResult.class);
        java.util.List<org.springframework.validation.FieldError> errors = java.util.List.of(
                new org.springframework.validation.FieldError("o", "amount", "must not be null"));
        org.mockito.Mockito.when(binding.getFieldErrors()).thenReturn(errors);
        MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        org.mockito.Mockito.when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ApiError> res = handler.handleValidationException(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).message()).isEqualTo("Validation failed");
        assertThat(body(res).errors()).isNotEmpty();
        assertThat(body(res).errors().get(0).field()).isEqualTo("amount");
    }
}