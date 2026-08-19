package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.dto.auth.SignupRequest;
import com.claimassist.platform.customer_service.service.CustomerSignupService;
import com.claimassist.platform.customer_service.service.OAuth2AuthorizationService;
import com.claimassist.platform.customer_service.service.OAuth2TokenService;
import com.claimassist.platform.customer_service.service.OAuth2LogoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private CustomerSignupService customerSignupService;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private OAuth2TokenService tokenService;

    @Mock
    private OAuth2LogoutService logoutService;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private PerformanceLogger performanceLogger;

    @InjectMocks
    private AuthController authController;

    private SignupRequest signupRequest;

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest("testuser", "Test User", "password123");
        lenient().doNothing().when(eventLogger).logBusinessEvent(anyString(), anyString(), anyMap());
        lenient().doNothing().when(performanceLogger).log(anyString(), anyString(), anyLong(), anyMap());
    }

    @Test
    void signup_ShouldReturnCreated() {
        // When
        ResponseEntity<Void> response = authController.signup(signupRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(customerSignupService).signup(signupRequest);
    }

    @Test
    void authorize_ShouldReturnFoundWithAuthorizationUrl() {
        // Given
        when(authorizationService.createAuthorizationRequest())
                .thenReturn(new OAuth2AuthorizationService.AuthorizationRequest(
                        "http://keycloak/auth?code=test",
                        "test-state"));

        // When
        ResponseEntity<Void> response = authController.authorize();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().get("Location")).isNotNull();
        verify(authorizationService).createAuthorizationRequest();
    }

    @Test
    void callback_WithError_ShouldThrowBadRequestException() {
        // When & Then
        assertThatThrownBy(() -> authController.callback(
                null, "access_denied", "User denied access", "state-123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Authorization failed: access_denied - User denied access");
    }

    @Test
    void callback_WithMissingCode_ShouldThrowBadRequestException() {
        // When & Then
        assertThatThrownBy(() -> authController.callback(
                null, null, null, "state-123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing authorization code");
    }

    @Test
    void callback_WithSuccess_ShouldReturnAuthResponse() {
        // Given
        when(authorizationService.consumeCodeVerifier("state-123")).thenReturn("test-verifier");
        when(tokenService.exchangeAuthorizationCode(anyString(), anyString()))
                .thenReturn(new AuthResponse(
                        "access-token", "refresh-token", "Bearer", 3600L, 3600L, "openid", "", 1L, "testuser"));

        // When
        ResponseEntity<AuthResponse> response = authController.callback(
                "authorization-code", null, null, "state-123");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().customerId()).isEqualTo(1L);
        verify(tokenService).exchangeAuthorizationCode("authorization-code", "test-verifier");
    }

    @Test
    void refresh_ShouldReturnAuthResponse() {
        // Given
when(tokenService.refreshToken(anyString())).thenReturn(new AuthResponse(
                        "new-access-token", "new-refresh-token", "Bearer", 3600L, 3600L, "openid", "", 1L, "testuser"));

        // When
        ResponseEntity<AuthResponse> response = authController.refresh("refresh-token-123");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().customerId()).isEqualTo(1L);
        verify(tokenService).refreshToken("refresh-token-123");
    }

    @Test
    void logout_ShouldReturnNoContent() {
        // When
        ResponseEntity<Void> response = authController.logout("refresh-token-123");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(logoutService).logout("refresh-token-123");
    }
}
