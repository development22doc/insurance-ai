package com.claimassist.platform.customer_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a shared {@link RestClient} bean for services that make outbound HTTP
 * calls to Keycloak. Spring Boot auto-configures {@link RestClient.Builder}; this
 * exposes a ready-to-inject {@link RestClient} instance.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
