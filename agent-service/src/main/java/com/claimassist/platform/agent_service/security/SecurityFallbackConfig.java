package com.claimassist.platform.agent_service.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Provide a noop JwtDecoder for local development when no real decoder is configured.
 * This allows the application to start without external Keycloak configuration.
 * <p>
 * This bean is gated on {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * being ABSENT (or explicitly set to the sentinel value {@code insecure-local}). It
 * must never preempt Spring Boot's real JWKS-based decoder when an issuer URI is
 * configured: the real decoder validates the token signature and preserves all claims
 * (notably {@code userId}), which user identification depends on.
 */
@Configuration
public class SecurityFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(
            name = "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            havingValue = "insecure-local",
            matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "local")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}

