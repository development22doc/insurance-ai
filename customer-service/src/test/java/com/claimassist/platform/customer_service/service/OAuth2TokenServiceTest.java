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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        lenient().when(customerRepository.findById(TEST_CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));

        // Setup RefreshTokenService
        lenient().when(refreshTokenService.createRefreshToken(any(Customer.class))).thenReturn(mock(RefreshToken.class));
        lenient().when(refreshTokenService.validateAndRotate(anyString())).thenReturn(mock(RefreshToken.class));
    }

    @Test
    void refreshToken_WithInvalidToken_ShouldThrowBadRequestException() {
        // Given
        when(refreshTokenService.validateAndRotate(TEST_REFRESH_TOKEN))
                .thenThrow(new BadRequestException("Invalid refresh token"));

        // When/Then
        assertThatThrownBy(() -> oauth2TokenService.refreshToken(TEST_REFRESH_TOKEN))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");

        // Verify token validation was attempted
        verify(refreshTokenService).validateAndRotate(TEST_REFRESH_TOKEN);

        // Verify RestClient was NOT called
        verify(restClient, never()).post();
    }

    @Test
    void refreshToken_WithValidToken_ShouldReturnAuthResponse() {
        // Given
        String validRefreshToken = "valid-refresh-token";
        RefreshToken mockRefreshToken = mock(RefreshToken.class);
        when(refreshTokenService.validateAndRotate(validRefreshToken)).thenReturn(mockRefreshToken);

        // Mock RestClient chain: post().uri().contentType().body().retrieve().body()
        // After .uri() the chain is RequestBodySpec throughout (body() returns
        // RequestBodySpec, not RequestHeadersSpec), then retrieve() -> ResponseSpec.
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(TEST_TOKEN_URI)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(LinkedMultiValueMap.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(successfulTokenResponse);

        // When
        AuthResponse response = oauth2TokenService.refreshToken(validRefreshToken);

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

        // Verify token validation was called
        verify(refreshTokenService).validateAndRotate(validRefreshToken);

        // Verify RestClient chain was called
        verify(restClient).post();
        verify(requestBodyUriSpec).uri(TEST_TOKEN_URI);
        verify(requestBodySpec).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        verify(requestBodySpec).body(any(LinkedMultiValueMap.class));
        verify(requestBodySpec).retrieve();
        verify(responseSpec).body(Map.class);

        // Verify customer lookup
        verify(customerRepository).findByUsername(TEST_USERNAME);

        // Verify refresh token creation for rotation
        verify(refreshTokenService).createRefreshToken(testCustomer);
    }

    @Test
    void refreshToken_WhenCustomerNotFound_ShouldReturnResponseWithoutCustomer() {
        // Given
        String validRefreshToken = "valid-refresh-token-user-missing";
        RefreshToken mockRefreshToken = mock(RefreshToken.class);
        when(refreshTokenService.validateAndRotate(validRefreshToken)).thenReturn(mockRefreshToken);

        // Customer not found by the username carried in the validated ID token
        when(customerRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // Mock RestClient chain: post().uri().contentType().body().retrieve().body()
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(TEST_TOKEN_URI)).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(LinkedMultiValueMap.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(successfulTokenResponse);

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
        // No rotation record created because no customer id is known
        verify(refreshTokenService, never()).createRefreshToken(any(Customer.class));
    }
}
