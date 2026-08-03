package com.claimassist.platform.common_lib.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceClientCredentialsTokenProvider {

    private static final String SYSTEM_PRINCIPAL = "claimassist-system";

    private final ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManagerProvider;

    @Value("${security.service-client.registration-id:internal-service}")
    private String clientRegistrationId;

    public String getAccessToken() {
        OAuth2AuthorizedClientManager oauth2AuthorizedClientManager = authorizedClientManagerProvider.getIfAvailable();
        if (oauth2AuthorizedClientManager == null) {
            throw new IllegalStateException("OAuth2AuthorizedClientManager is not available for client-credentials flow");
        }

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(SYSTEM_PRINCIPAL)
                .build();

        OAuth2AuthorizedClient authorizedClient = oauth2AuthorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new IllegalStateException(
                    "Client-credentials token acquisition failed for registration: " + clientRegistrationId);
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
