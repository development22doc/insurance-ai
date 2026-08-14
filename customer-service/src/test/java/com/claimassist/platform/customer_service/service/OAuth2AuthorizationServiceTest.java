package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthorizationServiceTest {

    @Mock
    private PkceService pkceService;

    @Mock
    private KeycloakProperties keycloakProperties;

    @InjectMocks
    private OAuth2AuthorizationService authorizationService;

    private static final String TEST_STATE = "test-state-123";
    private static final String TEST_CODE_VERIFIER = "test-code-verifier-abc";
    private static final String TEST_CODE_CHALLENGE = "test-code-challenge-xyz";
    private static final String TEST_AUTHORIZATION_URI = "http://localhost:8080/realms/test/protocol/openid-connect/auth";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_REDIRECT_URI = "http://localhost:3000/callback";

    @BeforeEach
    void setUp() {
        lenient().when(pkceService.generateState()).thenReturn(TEST_STATE);
        lenient().when(pkceService.generateCodeVerifier()).thenReturn(TEST_CODE_VERIFIER);
        lenient().when(pkceService.generateCodeChallenge(TEST_CODE_VERIFIER)).thenReturn(TEST_CODE_CHALLENGE);
        lenient().when(keycloakProperties.authorizationUri()).thenReturn(TEST_AUTHORIZATION_URI);
        lenient().when(keycloakProperties.clientId()).thenReturn(TEST_CLIENT_ID);
        lenient().when(keycloakProperties.redirectUri()).thenReturn(TEST_REDIRECT_URI);
    }

    @Test
    void createAuthorizationRequest_WithValidConfiguration_ShouldReturnAuthorizationRequest() {
        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.authorizationUrl()).isNotEmpty();
        assertThat(result.state()).isEqualTo(TEST_STATE);
        assertThat(result.authorizationUrl()).contains(TEST_AUTHORIZATION_URI);
        assertThat(result.authorizationUrl()).contains("client_id=" + TEST_CLIENT_ID);
        assertThat(result.authorizationUrl()).contains("response_type=code");
        assertThat(result.authorizationUrl()).contains("scope=openid%20profile%20email");
        assertThat(result.authorizationUrl()).contains("redirect_uri=" + TEST_REDIRECT_URI);
        assertThat(result.authorizationUrl()).contains("code_challenge=" + TEST_CODE_CHALLENGE);
        assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
        assertThat(result.authorizationUrl()).contains("state=" + TEST_STATE);

        verify(pkceService).generateState();
        verify(pkceService).generateCodeVerifier();
        verify(pkceService).generateCodeChallenge(TEST_CODE_VERIFIER);
        verify(keycloakProperties).authorizationUri();
        verify(keycloakProperties).clientId();
        verify(keycloakProperties).redirectUri();
    }

    @Test
    void createAuthorizationRequest_ShouldStoreCodeVerifierWithState() {
        // When
        authorizationService.createAuthorizationRequest();

        // Then - code verifier should be stored with the state
        String retrievedVerifier = authorizationService.consumeCodeVerifier(TEST_STATE);
        assertThat(retrievedVerifier).isEqualTo(TEST_CODE_VERIFIER);
    }

    @Test
    void createAuthorizationRequest_WithNullAuthorizationUri_ShouldThrowIllegalStateException() {
        // Given
        when(keycloakProperties.authorizationUri()).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authorizationService.createAuthorizationRequest())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authorization URI could not be constructed");
    }

    @Test
    void createAuthorizationRequest_WithEmptyAuthorizationUri_ShouldThrowIllegalStateException() {
        // Given
        when(keycloakProperties.authorizationUri()).thenReturn("");

        // When & Then
        assertThatThrownBy(() -> authorizationService.createAuthorizationRequest())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authorization URI could not be constructed");
    }

    @Test
    void createAuthorizationRequest_ShouldGenerateNewStateForEachCall() {
        // Given
        String state1 = "state-1";
        String state2 = "state-2";
        when(pkceService.generateState()).thenReturn(state1, state2);

        // When
        OAuth2AuthorizationService.AuthorizationRequest result1 = authorizationService.createAuthorizationRequest();
        OAuth2AuthorizationService.AuthorizationRequest result2 = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result1.state()).isEqualTo(state1);
        assertThat(result2.state()).isEqualTo(state2);
        assertThat(result1.state()).isNotEqualTo(result2.state());
    }

    @Test
    void createAuthorizationRequest_ShouldGenerateNewCodeVerifierForEachCall() {
        // Given
        String verifier1 = "verifier-1";
        String verifier2 = "verifier-2";
        when(pkceService.generateCodeVerifier()).thenReturn(verifier1, verifier2);
        when(pkceService.generateCodeChallenge(verifier1)).thenReturn("challenge-1");
        when(pkceService.generateCodeChallenge(verifier2)).thenReturn("challenge-2");

        // When
        authorizationService.createAuthorizationRequest();
        authorizationService.createAuthorizationRequest();

        // Then - each call should generate a new code verifier
        verify(pkceService, times(2)).generateCodeVerifier();
    }

    @Test
    void consumeCodeVerifier_WithValidState_ShouldReturnVerifierAndRemoveFromStore() {
        // Given
        authorizationService.createAuthorizationRequest();

        // When
        String verifier = authorizationService.consumeCodeVerifier(TEST_STATE);

        // Then
        assertThat(verifier).isEqualTo(TEST_CODE_VERIFIER);

        // Second call should return null (one-time use)
        String secondCall = authorizationService.consumeCodeVerifier(TEST_STATE);
        assertThat(secondCall).isNull();
    }

    @Test
    void consumeCodeVerifier_WithInvalidState_ShouldReturnNull() {
        // When
        String verifier = authorizationService.consumeCodeVerifier("invalid-state");

        // Then
        assertThat(verifier).isNull();
    }

    @Test
    void consumeCodeVerifier_WithEmptyState_ShouldReturnNull() {
        // When
        String verifier = authorizationService.consumeCodeVerifier("");

        // Then
        assertThat(verifier).isNull();
    }

    @Test
    void consumeCodeVerifier_ShouldProvideOneTimeUseSecurity() {
        // Given
        authorizationService.createAuthorizationRequest();

        // When
        String firstCall = authorizationService.consumeCodeVerifier(TEST_STATE);
        String secondCall = authorizationService.consumeCodeVerifier(TEST_STATE);

        // Then - security: code verifier can only be consumed once
        assertThat(firstCall).isEqualTo(TEST_CODE_VERIFIER);
        assertThat(secondCall).isNull();
    }

    @Test
    void createAuthorizationRequest_ShouldUseS256CodeChallengeMethod() {
        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then - security: should use S256 (SHA-256) for code challenge method
        assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
    }

    @Test
    void createAuthorizationRequest_ShouldIncludeCorrectScope() {
        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result.authorizationUrl()).contains("scope=openid%20profile%20email");
    }

    @Test
    void createAuthorizationRequest_ShouldUseResponseTypeCode() {
        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result.authorizationUrl()).contains("response_type=code");
    }

    @Test
    void authorizationRequestRecord_ShouldHoldValuesCorrectly() {
        // Given
        String testUrl = "http://test.com/auth";
        String testState = "test-state";

        // When
        OAuth2AuthorizationService.AuthorizationRequest request =
                new OAuth2AuthorizationService.AuthorizationRequest(testUrl, testState);

        // Then
        assertThat(request.authorizationUrl()).isEqualTo(testUrl);
        assertThat(request.state()).isEqualTo(testState);
    }

    @Test
    void consumeCodeVerifier_AfterMultipleAuthorizationRequests_ShouldReturnCorrectVerifierForEachState() {
        // Given
        String state1 = "state-1";
        String state2 = "state-2";
        String verifier1 = "verifier-1";
        String verifier2 = "verifier-2";

        when(pkceService.generateState()).thenReturn(state1, state2);
        when(pkceService.generateCodeVerifier()).thenReturn(verifier1, verifier2);
        when(pkceService.generateCodeChallenge(verifier1)).thenReturn("challenge-1");
        when(pkceService.generateCodeChallenge(verifier2)).thenReturn("challenge-2");

        // When
        authorizationService.createAuthorizationRequest();
        authorizationService.createAuthorizationRequest();

        // Then - each state should return its corresponding verifier
        assertThat(authorizationService.consumeCodeVerifier(state1)).isEqualTo(verifier1);
        assertThat(authorizationService.consumeCodeVerifier(state2)).isEqualTo(verifier2);
    }

    @Test
    void createAuthorizationRequest_WithDifferentClientIds_ShouldUseConfiguredClientId() {
        // Given
        String customClientId = "custom-client-id";
        when(keycloakProperties.clientId()).thenReturn(customClientId);

        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result.authorizationUrl()).contains("client_id=" + customClientId);
    }

    @Test
    void createAuthorizationRequest_WithDifferentRedirectUri_ShouldUseConfiguredRedirectUri() {
        // Given
        String customRedirectUri = "http://custom-redirect.com/callback";
        when(keycloakProperties.redirectUri()).thenReturn(customRedirectUri);

        // When
        OAuth2AuthorizationService.AuthorizationRequest result = authorizationService.createAuthorizationRequest();

        // Then
        assertThat(result.authorizationUrl()).contains("redirect_uri=" + customRedirectUri);
    }

    @Test
    void createAuthorizationRequest_WithNullKeycloakProperties_ShouldThrowIllegalStateException() {
        // Given - manually create service instance with null KeycloakProperties
        OAuth2AuthorizationService serviceWithNullProps = new OAuth2AuthorizationService(pkceService, null);

        // When & Then - defensive check for null KeycloakProperties
        assertThatThrownBy(() -> serviceWithNullProps.createAuthorizationRequest())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KeycloakProperties is not configured");
    }
}
