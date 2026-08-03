package com.claimassist.platform.claims_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Registers OAuth2 clients for service-to-service authentication.
 * Client credentials are loaded from properties: security.service-client.*
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ClientRegistrationConfiguration {

    private final OAuth2ClientConfig.OAuth2ClientProperties clientProperties;

    /**
     * Registers the internal service client for client-credentials flow.
     * Only creates the bean if client ID and secret are configured.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "security.service-client",
            name = "clientId",
            matchIfMissing = false
    )
    public ClientRegistrationRepository clientRegistrationRepository() {
        log.info("Registering OAuth2 client for service-to-service authentication: {}",
                clientProperties.getRegistrationId());

        ClientRegistration clientRegistration = ClientRegistration
                .withRegistrationId(clientProperties.getRegistrationId())
                .clientId(clientProperties.getClientId())
                .clientSecret(clientProperties.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(clientProperties.getTokenUri())
                .scope("openid", "profile", "email")
                .build();

        return new InMemoryClientRegistrationRepository(clientRegistration);
    }
}

