package com.claimassist.platform.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Custom filter to add comprehensive security headers to API Gateway responses.
 * Complements the ServerHttpSecurity header configuration with additional headers
 * that are not available in the reactive configuration API.
 */
@Slf4j
@Configuration
public class GatewaySecurityHeadersFilter {

    @Bean
    public WebFilter securityHeadersWebFilter() {
        return (exchange, chain) -> chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // Only add security headers if the response is still mutable.
                    // This avoids attempting to modify headers after the response
                    // has been committed (which causes "ServerHttpResponse already committed").
                    org.springframework.http.server.reactive.ServerHttpResponse response = exchange.getResponse();
                    if (!response.isCommitted()) {
                        addSecurityHeaders(response);
                    }
                }));
    }

    private void addSecurityHeaders(org.springframework.http.server.reactive.ServerHttpResponse response) {
        // X-Content-Type-Options: Prevent MIME type sniffing
        response.getHeaders().set("X-Content-Type-Options", "nosniff");

        // X-XSS-Protection: Legacy XSS protection
        response.getHeaders().set("X-XSS-Protection", "1; mode=block");

        // Cache-Control: Prevent caching of sensitive content
        response.getHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        response.getHeaders().set("Pragma", "no-cache");
        response.getHeaders().set("Expires", "0");
    }
}

