package com.claimassist.platform.common_lib.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for servlet-based services.
 * Configures cross-origin resource sharing with restrictive defaults.
 * Individual services can override allowedOrigins via environment variables.
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorsConfigurationHandler {

    /**
     * Creates a CORS configuration source with restrictive defaults.
     * Allows credentials and specific HTTP methods for secure cross-origin communication.
     *
     * Allowed Origins: Configurable via CORS_ALLOWED_ORIGINS env var (comma-separated)
     * Default: http://localhost:3000,http://localhost:4200 (local development only)
     *
     * Allowed Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
     * Allowed Headers: Content-Type, Authorization, X-Requested-With, Correlation-ID
     * Exposed Headers: Authorization, Content-Type, Correlation-ID
     * Allow Credentials: true (allows cookies/auth headers in requests)
     * Max Age: 3600 seconds (1 hour)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Get allowed origins from environment variable, with safe local defaults
        String allowedOriginsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> allowedOrigins;

        if (allowedOriginsEnv != null && !allowedOriginsEnv.trim().isEmpty()) {
            allowedOrigins = Arrays.asList(allowedOriginsEnv.split(","));
        } else {
            // Local development defaults - no production use
            allowedOrigins = Arrays.asList(
                    "http://localhost:3000",   // React dev server
                    "http://localhost:4200",   // Angular dev server
                    "http://localhost:8080"    // Local gateway
            );
            log.warn("No CORS_ALLOWED_ORIGINS env var set. Using local development defaults.");
        }

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Correlation-ID",
                "Accept",
                "Origin"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Correlation-ID"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);  // 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration initialized with allowed origins: {}", allowedOrigins);
        return source;
    }
}

