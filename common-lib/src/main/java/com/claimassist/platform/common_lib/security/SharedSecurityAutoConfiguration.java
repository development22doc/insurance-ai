package com.claimassist.platform.common_lib.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.observability.DeveloperIdentity;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import org.springframework.context.annotation.Primary;

@Slf4j

@AutoConfiguration
// Only load this auto-configuration for servlet (non-reactive) web applications
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// Also ensure servlet API is present before attempting to register servlet-based beans
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@RequiredArgsConstructor
public class SharedSecurityAutoConfiguration {

    /**
     * Configures the OAuth2AuthorizedClientManager for web/servlet environments.
     * Uses OAuth2AuthorizedClientRepository for persistence across requests.
     * Only created if both ClientRegistrationRepository and OAuth2AuthorizedClientRepository are available.
     * This bean is conditionally created and serves as a fallback for servlet environments
     * if the primary authorizedClientServiceManager is not suitable.
     */
    @Bean(name = "webEnvironmentAuthorizedClientManager")
    public OAuth2AuthorizedClientManager webEnvironmentAuthorizedClientManager(
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider) {

        ClientRegistrationRepository clientRegistrationRepository =
                clientRegistrationRepositoryProvider.getIfAvailable();
        OAuth2AuthorizedClientRepository authorizedClientRepository =
                authorizedClientRepositoryProvider.getIfAvailable();

        if (clientRegistrationRepository == null || authorizedClientRepository == null) {
            log.debug("ClientRegistrationRepository or OAuth2AuthorizedClientRepository not available - OAuth2 client-credentials flow disabled for web environments in this service");
            return null;
        }

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()  // Enable client-credentials grant type
                        .refreshToken()        // Enable refresh token grant type
                        .build();

        DefaultOAuth2AuthorizedClientManager webAuthorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository);

        webAuthorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return webAuthorizedClientManager;
    }

    /**
     * Primary bean for OAuth2AuthorizedClientManager - for non-servlet environments.
     * Used in Kafka consumers, scheduled tasks, batch jobs, and other background services.
     * Uses in-memory client service for simplicity; does not require an HTTP session.
     * Implements token caching and automatic renewal before expiry.
     *
     * This is marked as @Primary because it's the most commonly needed implementation
     * across the ClaimAssist platform for service-to-service authentication.
     * Only created if ClientRegistrationRepository is available
     * (i.e., if security.service-client.clientId is configured).
     */
    @Bean
    @Primary
    public AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientServiceManager(
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) {

        ClientRegistrationRepository repository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("ClientRegistrationRepository not available - OAuth2 client-credentials flow disabled for this service");
            return null;
        }

        InMemoryOAuth2AuthorizedClientService clientService =
                new InMemoryOAuth2AuthorizedClientService(repository);

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .refreshToken()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        repository,
                        clientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        log.debug("AuthorizedClientServiceOAuth2AuthorizedClientManager (primary) bean created successfully");
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
    public CorrelationIdFilter correlationIdFilter (DeveloperIdentity developerIdentity) {
        return new CorrelationIdFilter (developerIdentity);
    }

    /**
     * Propagates the authenticated end-user JWT and correlation ID on every
     * Feign call. Each downstream service validates the forwarded JWT
     * independently using its own OAuth2 Resource Server configuration.
     * For non-request threads (scheduled jobs/Kafka consumers/background
     * tasks), where no end-user JWT exists, falls back to client-credentials.
     */
    @Bean
    public RequestInterceptor requestInterceptor (ObjectProvider<ServiceClientCredentialsTokenProvider> tokenProviderProvider) {

        return requestTemplate -> {

            Authentication authentication =
                    SecurityContextHolder.getContext ().getAuthentication ();

            if (authentication != null &&
                    authentication.getPrincipal () instanceof Jwt jwt) {

                requestTemplate.header (
                        "Authorization",
                        "Bearer " + jwt.getTokenValue ());
            } else {
                ServiceClientCredentialsTokenProvider tokenProvider = tokenProviderProvider.getIfAvailable();
                if (tokenProvider != null) {
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
