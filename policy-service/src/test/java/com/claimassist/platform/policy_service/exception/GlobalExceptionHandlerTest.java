package com.claimassist.platform.policy_service.exception;

import com.claimassist.platform.common_lib.error.ApiError;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_returnsBadRequestStatus() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(new BadRequestException("Invalid credentials"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void handleAuthorizationFailure_returnsForbiddenResponse() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(new AccessDeniedException("Missing role"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().message());
    }

    @Test
    void handleAuthenticationFailure_returnsUnauthorizedResponse() {
        ResponseEntity<ApiError> response = handler.handleAuthenticationException(new BadCredentialsException("bad token"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid credentials", response.getBody().message());
    }

    @Test
    void handleUnexpectedException_returnsGenericServerError() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertNotNull(response.getBody());
    }

    @Test
    void handleResourceNotFound_returnsNotFoundResponse() {
        ResponseEntity<ApiError> response = handler.handleResourceNotFound(new ResourceNotFoundException("Purchase", "42"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Purchase not found with id: 42", response.getBody().message());
    }
}
