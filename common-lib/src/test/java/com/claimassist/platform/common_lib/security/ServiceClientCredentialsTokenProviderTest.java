package com.claimassist.platform.common_lib.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceClientCredentialsTokenProviderTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<OAuth2AuthorizedClientManager> providerOf(OAuth2AuthorizedClientManager manager) {
        ObjectProvider<OAuth2AuthorizedClientManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(manager);
        return provider;
    }

    private ServiceClientCredentialsTokenProvider provider(OAuth2AuthorizedClientManager manager) {
        ServiceClientCredentialsTokenProvider provider = new ServiceClientCredentialsTokenProvider(providerOf(manager));
        setRegistrationId(provider, "internal-service");
        return provider;
    }

    private void setRegistrationId(ServiceClientCredentialsTokenProvider provider, String id) {
        try {
            Field field = ServiceClientCredentialsTokenProvider.class.getDeclaredField("clientRegistrationId");
            field.setAccessible(true);
            field.set(provider, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OAuth2AccessToken accessToken(String value) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, Instant.now(), Instant.now().plusSeconds(300));
    }

    @Test
    void returnsAccessTokenFromAuthorizedClientManager() throws Exception {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(accessToken("service.access.token"));
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorized);

        ServiceClientCredentialsTokenProvider provider = provider(manager);
        assertThat(provider.getAccessToken()).isEqualTo("service.access.token");
    }

    @Test
    void throwsIllegalStateWhenNoManagerAvailable() {
        ServiceClientCredentialsTokenProvider provider = provider(null);
        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth2AuthorizedClientManager is not available");
    }

    @Test
    void throwsIllegalStateWhenAuthorizeReturnsNull() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);
        ServiceClientCredentialsTokenProvider provider = provider(manager);
        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client-credentials token acquisition failed");
    }

    @Test
    void throwsIllegalStateWhenAuthorizedClientHasNoAccessToken() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(null);
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorized);
        ServiceClientCredentialsTokenProvider provider = provider(manager);
        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client-credentials token acquisition failed");
    }

    @Test
    void usesConfiguredClientRegistrationId() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        ServiceClientCredentialsTokenProvider provider = provider(manager);
        setRegistrationId(provider, "internal-service");

        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(accessToken("tok"));
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenAnswer(inv -> {
            OAuth2AuthorizeRequest req = inv.getArgument(0);
            assertThat(req.getClientRegistrationId()).isEqualTo("internal-service");
            return authorized;
        });

        assertThat(provider.getAccessToken()).isEqualTo("tok");
    }
}