package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.api_gateway.properties.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies CORS is restricted to explicit origins and never combined with wildcard
 * credentials - matching the production policy (no {@code allowedOrigins = "*"}).
 */
class CorsConfigurationTest {

    private GatewaySecurityConfig newConfig() {
        return new GatewaySecurityConfig(new SecurityProperties(java.util.List.of("/actuator/health")),
                new ObjectMapper());
    }

    private CorsConfiguration cors(String property) {
        GatewaySecurityConfig config = newConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsProperty", property);
        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(MockServerWebExchange.from(MockServerHttpRequest.get("/")));
    }

    @Test
    void usesConfiguredOriginsWhenPropertySet() {
        CorsConfiguration cors = cors("https://app.claimassist.example.com,https://admin.claimassist.example.com");
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://app.claimassist.example.com", "https://admin.claimassist.example.com");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void wildcardOriginDisablesCredentials() {
        CorsConfiguration cors = cors("*");
        assertThat(cors.getAllowedOrigins()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isFalse();
    }

    @Test
    void trimsWhitespaceAndSkipsEmptyEntries() {
        CorsConfiguration cors = cors(" https://app.claimassist.example.com , , https://b.claimassist.example.com ");
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://app.claimassist.example.com", "https://b.claimassist.example.com");
    }

    @Test
    void fallsBackToLocalDevelopmentDefaultsWhenUnset() {
        CorsConfiguration cors = cors("");
        assertThat(cors.getAllowedOrigins())
                .contains("http://localhost:3000", "http://localhost:4200", "http://localhost:8080");
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}