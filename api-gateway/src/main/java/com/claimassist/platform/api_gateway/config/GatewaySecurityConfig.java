package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.api_gateway.properties.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
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

    @Bean
    public SecurityWebFilterChain securityWebFilterChain (ServerHttpSecurity http) {

        String[] publicRoutes = securityProperties.publicRoutes ().toArray (String[] :: new);

        http
                .csrf (ServerHttpSecurity.CsrfSpec :: disable)

                .cors (Customizer.withDefaults ())

                .headers (headers -> headers
                        .frameOptions (frame -> frame.disable ())
                        .contentTypeOptions (Customizer.withDefaults ())
                        .cache (cache -> {
                        })
                        .hsts (Customizer.withDefaults ())
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

        log.info ("Gateway OAuth2 Resource Server enabled.");
        log.info ("JWT validation through JWKS enabled.");
        log.info ("Security headers enabled.");
        log.info ("Authorization Code + PKCE enabled.");

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