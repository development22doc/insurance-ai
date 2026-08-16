package com.claimassist.platform.customer_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded timeouts for outbound HTTP calls to Keycloak. Every Keycloak call
 * (token, logout, admin provisioning) shares the {@link RestClientConfig}
 * {@code RestClient}, so a hung Keycloak can never stall a customer request
 * indefinitely. Defaults match sensible production values and are overridable
 * via {@code keycloak.http.connect-timeout-ms} / {@code keycloak.http.read-timeout-ms}.
 */
@ConfigurationProperties(prefix = "keycloak.http")
public record KeycloakHttpProperties(
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public KeycloakHttpProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 3000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 5000;
        }
    }
}