package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2 refresh-token flow tests: Keycloak is authoritative for the OAuth2
 * exchange (called FIRST), then the locally persisted Keycloak token is atomically
 * rotated via {@link RefreshTokenService#rotateIfPresent} and the local revoked /
 * expired guard can reject a presented token as defense-in-depth.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2TokenServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private KeycloakProperties keycloakProperties;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private PerformanceLogger performanceLogger;

    @InjectMocks
    private OAuth2TokenService oauth2TokenService;

    private static final String TEST_USERNAME = "testuser@example.com";
    private static final Long TEST_CUSTOMER_ID = 1L;
    private static final String TEST_FULL_NAME = "Test User";
    private static final String TEST_ID_TOKEN = "id-token-xyz";
    private static final String TEST_ACCESS_TOKEN = "access-token-xyz";
    private static final String TEST_REFRESH_TOKEN = "refresh-token-xyz";
    private static final String TEST_TOKEN_URI = "http://keycloak/realms/test/protocol/openid-connect/token";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CLIENT_SECRET = "test-secret";

    private Customer testCustomer;
    private Jwt testJwt;
    private Map<String, Object> successfulTokenResponse;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(TEST_CUSTOMER_ID)
                .username(TEST_USERNAME)
                .fullName(TEST_FULL_NAME)
                .keycloakId("keycloak-123")
                .build();

        testJwt = mock(Jwt.class);
        lenient().when(testJwt.getClaimAsString("preferred_username")).thenReturn(TEST_USERNAME);
        lenient().when(testJwt.getClaimAsString("email")).thenReturn(null);

        successfulTokenResponse = Map.of(
                "access_token", TEST_ACCESS_TOKEN,
                "refresh_token", TEST_REFRESH_TOKEN,
                "token_type", "Bearer",
                "expires_in", 300L,
                "refresh_expires_in", 1800L,
                "scope", "openid profile email",
                "id_token", TEST_ID_TOKEN
        );

        // Setup KeycloakProperties
        lenient().when(keycloakProperties.clientId()).thenReturn(TEST_CLIENT_ID);
        lenient().when(keycloakProperties.clientSecret()).thenReturn(TEST_CLIENT_SECRET);
        lenient().when(keycloakProperties.tokenUri()).thenReturn(TEST_TOKEN_URI);

        // Setup JwtDecoder
        lenient().when(jwtDecoder.decode(TEST_ID_TOKEN)).thenReturn(testJwt);

        // Setup CustomerRepository
        lenient().when(customerRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testCustomer));
    }

    /**
     * KEYCLOAK FIRST: Keycloak is authoritative for validating and rotating the old
     * token. When Keycloak returns a NEW refresh token, we then atomically rotate the
     * local record. If that local record is revoked, the defense-in-depth guard throws.
     */
    @Test
    void refreshToken_WhenLocalRecordRevoked_ShouldThrowBadRequestException() {
        // Given: Keycloak rotates with a NEW token, customer is found, but the local
        // record is revoked -> the local guard rejects it.
        String oldRefreshToken = "revoked-local-token";
        String newRefreshToken = "new-token-from-keycloak";
        Map<String, Object> rotatedResponse = new HashMap<>(successfulTokenResponse);
        rotatedResponse.put("refresh_token", newRefreshToken);

        mockKeycloakTokenExchange(rotatedResponse);

        when(refreshTokenService.rotateIfPresent(
                eq(oldRefreshToken), eq(newRefreshToken), any(Instant.class), any()))
                .thenThrow(new BadRequestException("Refresh token revoked"));

        // When/Then
        assertThatThrownBy(() -> oauth2TokenService.refreshToken(oldRefreshToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Refresh token revoked");

        // Keycloak was consulted FIRST (authoritative)
        verify(restClient).post();
        // Local atomic rotation was attempted on the presented old token
        verify(refreshTokenService).rotateIfPresent(
                eq(oldRefreshToken), eq(newRefreshToken), any(Instant.class), any());
    }

    @Test
    void refreshToken_WithValidToken_ShouldReturnAuthResponse() {
        // Given: Keycloak returns a successful response with a NEW refresh token and
        // the customer is found; rotation of the local record succeeds.
        String oldRefreshToken = "valid-refresh-token";
        String newRefreshToken = TEST_REFRESH_TOKEN;

        mockKeycloakTokenExchange(successfulTokenResponse);

        when(refreshTokenService.rotateIfPresent(
                eq(oldRefreshToken), eq(newRefreshToken), any(Instant.class), any()))
                .thenReturn(Optional.of(mock(RefreshToken.class)));

        // When
        AuthResponse response = oauth2TokenService.refreshToken(oldRefreshToken);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(300L);
        assertThat(response.refreshExpiresIn()).isEqualTo(1800L);
        assertThat(response.scope()).isEqualTo("openid profile email");
        assertThat(response.idToken()).isEqualTo(TEST_ID_TOKEN);
        assertThat(response.customerId()).isEqualTo(TEST_CUSTOMER_ID);
        assertThat(response.fullName()).isEqualTo(TEST_FULL_NAME);

        // Verify Keycloak exchange was performed
        verify(restClient).post();
        // Verify customer lookup
        verify(customerRepository).findByUsername(TEST_USERNAME);
        // Verify atomic local rotation old -> new Keycloak token
        verify(refreshTokenService).rotateIfPresent(
                eq(oldRefreshToken), eq(newRefreshToken), any(Instant.class), any());
    }

    @Test
    void refreshToken_WhenCustomerNotFound_ShouldReturnResponseWithoutCustomer() {
        // Given: Keycloak returns tokens, but no customer matches the validated ID token.
        String validRefreshToken = "valid-refresh-token-user-missing";

        mockKeycloakTokenExchange(successfulTokenResponse);

        when(customerRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // When
        AuthResponse response = oauth2TokenService.refreshToken(validRefreshToken);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
        assertThat(response.idToken()).isEqualTo(TEST_ID_TOKEN);
        // No customer resolved, so customer fields must be null
        assertThat(response.customerId()).isNull();
        assertThat(response.fullName()).isNull();

        // Customer was looked up but not found
        verify(customerRepository).findByUsername(TEST_USERNAME);
        // No local rotation performed when no customer id is known
        verify(refreshTokenService, never()).rotateIfPresent(any(), any(), any(), any());
    }

    private void mockKeycloakTokenExchange(Map<String, Object> response) {
        // Mock RestClient chain: post().uri().contentType().body().retrieve().body()
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(TEST_TOKEN_URI)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(LinkedMultiValueMap.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(response);
    }
}