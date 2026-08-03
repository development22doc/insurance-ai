package com.claimassist.platform.claims_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth2 client configuration for machine-to-machine (service-to-service) authentication.
 */
@Configuration
@EnableConfigurationProperties(OAuth2ClientConfig.OAuth2ClientProperties.class)
public class OAuth2ClientConfig {

    @Data
    @ConfigurationProperties(prefix = "security.service-client")
    public static class OAuth2ClientProperties {

        /**
         * Client registration ID for service-to-service authentication.
         * Default: "internal-service"
         */
        private String registrationId = "internal-service";

        /**
         * OAuth2 client ID for this service (client credentials).
         */
        private String clientId;

        /**
         * OAuth2 client secret for this service.
         */
        private String clientSecret;

        /**
         * Token endpoint for client-credentials grant.
         */
        private String tokenUri;
    }
}

