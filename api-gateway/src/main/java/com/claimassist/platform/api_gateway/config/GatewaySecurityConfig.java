package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.api_gateway.properties.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GatewaySecurityConfig {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8180/realms/claimassist/protocol/openid-connect/certs}")
    private String jwkSetUri;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Get allowed origins from environment variable, with safe defaults
        String allowedOriginsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> allowedOrigins;

        if (allowedOriginsEnv != null && !allowedOriginsEnv.trim().isEmpty()) {
            allowedOrigins = Arrays.asList(allowedOriginsEnv.split(","));
        } else {
            // Local development defaults
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

        log.debug("API Gateway CORS configuration initialized with allowed origins: {}", allowedOrigins);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        log.debug("Creating ReactiveJwtDecoder with JWK Set URI: {}", jwkSetUri);
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain (ServerHttpSecurity http) {

        List<String> publicRoutesList = securityProperties.publicRoutes();
        if (publicRoutesList == null || publicRoutesList.isEmpty()) {
            // Provide safe defaults when no public routes are configured to avoid
            // Spring Security "matchers cannot be empty" error. These are safe
            // development defaults and include basic health endpoints and root.
            publicRoutesList = java.util.List.of("/actuator/health", "/actuator/info", "/");
            log.warn("No public routes configured (app.security.publicRoutes). Using safe defaults: {}", publicRoutesList);
        }

        String[] publicRoutes = publicRoutesList.toArray(String[]::new);

        http
                .csrf (ServerHttpSecurity.CsrfSpec :: disable)

                .cors (cors -> cors.configurationSource((CorsConfigurationSource) corsConfigurationSource()))

                .headers (headers -> headers
                        .frameOptions (frame -> frame.mode(
                                org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "upgrade-insecure-requests; " +
                                        "block-all-mixed-content"))
                        .referrerPolicy (referrer -> referrer.policy(
                                org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                .authorizeExchange (exchange -> exchange
                        .pathMatchers (publicRoutes).permitAll ()
                        .anyExchange ().authenticated ()
                )

                .oauth2ResourceServer (oauth2 -> oauth2
                        .jwt (jwt -> jwt.jwtAuthenticationConverter (gatewayJwtAuthenticationConverter ()))
                        .authenticationEntryPoint ((exchange, ex) ->
                                writeError (exchange.getResponse (),
                                        HttpStatus.UNAUTHORIZED,
                                        "Missing or invalid bearer token"))
                        .accessDeniedHandler ((exchange, denied) ->
                                writeError (exchange.getResponse (),
                                        HttpStatus.FORBIDDEN,
                                        "Access denied"))
                );

        log.debug("API Gateway security configuration: OAuth2 Resource Server + JWT validation via JWKS + comprehensive security headers");

        return http.build ();
    }

    private ReactiveJwtAuthenticationConverterAdapter gatewayJwtAuthenticationConverter () {

        Converter<Jwt, AbstractAuthenticationToken> delegate =
                jwt -> new JwtAuthenticationToken (jwt, extractAuthorities (jwt));

        return new ReactiveJwtAuthenticationConverterAdapter (delegate);
    }

    @SuppressWarnings ("unchecked")
    private Set<GrantedAuthority> extractAuthorities (Jwt jwt) {

        Set<GrantedAuthority> authorities = new HashSet<> ();

        Map<String, Object> realmAccess = jwt.getClaim ("realm_access");

        if (realmAccess != null && realmAccess.get ("roles") instanceof List<?> roles) {
            roles.forEach (role ->
                    authorities.add (new SimpleGrantedAuthority ("ROLE_" + role)));
        }

        Map<String, Object> resourceAccess = jwt.getClaim ("resource_access");

        if (resourceAccess != null) {

            resourceAccess.values ().forEach (client -> {

                if (client instanceof Map<?, ?> clientMap &&
                        clientMap.get ("roles") instanceof List<?> roles) {

                    roles.forEach (role ->
                            authorities.add (new SimpleGrantedAuthority ("ROLE_" + role)));
                }
            });
        }

        return authorities;
    }

    private Mono<Void> writeError (ServerHttpResponse response,
                                   HttpStatus status,
                                   String message) {

        response.setStatusCode (status);
        response.getHeaders ().setContentType (MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of (
                "status", status.value (),
                "error", status.getReasonPhrase (),
                "message", message,
                "timestamp", Instant.now ().toString ()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes (body);
            return response.writeWith (
                    Mono.just (response.bufferFactory ().wrap (bytes)));
        } catch (Exception e) {
            return response.setComplete ();
        }
    }
}
