package com.claimassist.platform.common_lib.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@AutoConfiguration
public class SharedSecurityAutoConfiguration {

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