package com.claimassist.platform.common_lib.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;

@Slf4j

@AutoConfiguration
@RequiredArgsConstructor
public class SharedSecurityAutoConfiguration {

    /**
     * Configures the OAuth2AuthorizedClientManager for client-credentials flow.
     * Uses in-memory storage for simplicity; does not require an HTTP session.
     * Implements token caching and automatic renewal before expiry.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()  // Enable client-credentials grant type
                        .refreshToken()        // Enable refresh token grant type
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    /**
     * Alternative bean for non-servlet environments (Kafka, scheduled tasks, batch jobs).
     * Uses in-memory client service.
     */
    @Bean
    public AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientServiceManager(
            ClientRegistrationRepository clientRegistrationRepository) {

        InMemoryOAuth2AuthorizedClientService clientService =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .refreshToken()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        clientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    @Bean
    public CurrentUserProvider currentUserProvider () {
        return new CurrentUserProvider ();
    }

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter () {
        return new KeycloakJwtAuthenticationConverter ();
    }

    @Bean
    public CorrelationIdFilter correlationIdFilter () {
        return new CorrelationIdFilter ();
    }

    /**
     * Propagates the authenticated end-user JWT and correlation ID on every
     * Feign call. Each downstream service validates the forwarded JWT
     * independently using its own OAuth2 Resource Server configuration.
     * For non-request threads (scheduled jobs/Kafka consumers/background
     * tasks), where no end-user JWT exists, falls back to client-credentials.
     */
    @Bean
    public RequestInterceptor requestInterceptor (ServiceClientCredentialsTokenProvider tokenProvider) {

        return requestTemplate -> {

            Authentication authentication =
                    SecurityContextHolder.getContext ().getAuthentication ();

            if (authentication != null &&
                    authentication.getPrincipal () instanceof Jwt jwt) {

                requestTemplate.header (
                        "Authorization",
                        "Bearer " + jwt.getTokenValue ());
            } else {
                try {
                    requestTemplate.header(
                            "Authorization",
                            "Bearer " + tokenProvider.getAccessToken());
                } catch (IllegalStateException ignored) {
                    // No client-credentials registration in this service/profile.
                    // Keep request untouched here - downstream call will fail normally
                    // if auth is required, which is preferable to startup failure.
                }
            }

            String correlationId =
                    MDC.get (CorrelationIdFilter.MDC_KEY);

            if (correlationId != null) {
                requestTemplate.header (
                        CorrelationIdFilter.CORRELATION_ID_HEADER,
                        correlationId);
            }
        };
    }
}