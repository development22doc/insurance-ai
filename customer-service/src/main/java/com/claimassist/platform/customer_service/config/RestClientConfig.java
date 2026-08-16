package com.claimassist.platform.customer_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides a shared {@link RestClient} bean for services that make outbound HTTP
 * calls to Keycloak. Spring Boot auto-configures {@link RestClient.Builder}; this
 * exposes a ready-to-inject {@link RestClient} instance.
 * <p>
 * The request factory applies <b>bounded timeouts</b> (see
 * {@link KeycloakHttpProperties}) so a slow or hung Keycloak fails fast with a
 * client-side error instead of stalling a customer/signup request indefinitely.
 * All Keycloak-bound services (OAuth2TokenService, OAuth2LogoutService,
 * KeycloakUserProvisioningService) inject this single bean and inherit the
 * timeouts.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder, KeycloakHttpProperties http) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(http.connectTimeoutMs());
        factory.setReadTimeout(http.readTimeoutMs());
        return builder.requestFactory(factory).build();
    }
}