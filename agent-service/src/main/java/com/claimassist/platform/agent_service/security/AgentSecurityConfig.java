package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.common_lib.observability.DeveloperIdentity;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.security.CallerType;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Configuration
@EnableReactiveMethodSecurity
@EnableWebFluxSecurity
@Slf4j
public class AgentSecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("Initializing Agent Service Reactive Security Filter Chain.");

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable()) // CORS handled by API Gateway
                .headers(headers -> headers.disable())
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/agent/stream").permitAll() // TEMPORARY: for testing the stream fix
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(keycloakJwtAuthenticationConverter())))
                );

        return http.build();
    }

    @Bean
    @Primary
    public com.claimassist.platform.common_lib.security.CurrentUserProvider reactiveCurrentUserProvider() {
        return new com.claimassist.platform.common_lib.security.CurrentUserProvider() {
            @Override
            public Long getCurrentUserId() {
                return extractUserId(getCurrentJwt());
            }

            @Override
            public String getCurrentUsername() {
                return getCurrentJwt().getClaimAsString("preferred_username");
            }

            @Override
            public String getCurrentName() {
                String name = getCurrentJwt().getClaimAsString("name");
                return name != null ? name : getCurrentUsername();
            }

            @Override
            public org.springframework.security.oauth2.jwt.Jwt getCurrentJwt() {
                // In a reactive WebFlux environment callers must obtain the JWT from
                // the Reactor SecurityContext (ReactiveSecurityContextHolder) rather
                // than synchronously via a ThreadLocal. Throw an explicit exception
                // here to fail fast if legacy blocking code attempts to call this
                // method on a reactor thread — this prevents accidental block() calls.
                throw new IllegalStateException(
                        "Synchronous CurrentUserProvider.getCurrentJwt() is not supported in reactive code. " +
                                "Use ReactiveSecurityContextHolder.getContext() and extract the Jwt reactively.");
            }

            @Override
            public CallerType callerType() {
                throw new IllegalStateException("Synchronous callerType() is not supported in reactive code. Use reactive security context instead.");
            }

            @Override
            public boolean isServiceToken() {
                throw new IllegalStateException("Synchronous isServiceToken() is not supported in reactive code. Use reactive security context instead.");
            }

            @Override
            public boolean isUserToken() {
                throw new IllegalStateException("Synchronous isUserToken() is not supported in reactive code. Use reactive security context instead.");
            }

            @Override
            public String getServiceClientId() {
                throw new IllegalStateException("Synchronous getServiceClientId() is not supported in reactive code. Use reactive security context instead.");
            }

            private Long extractUserId(org.springframework.security.oauth2.jwt.Jwt jwt) {
                if (classify(jwt) == CallerType.SERVICE) {
                    throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                            "The current token is a service (client-credentials) token and does not carry an end-user " +
                                    "'userId' claim. A service caller must act on behalf of a specific user only via a " +
                                    "propagated end-user token or an explicit user-id parameter, never via a userId claim.");
                }
                Object userId = jwt.getClaim("userId");
                if (userId instanceof Number number) {
                    return number.longValue();
                }
                return Long.valueOf(userId.toString());
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter correlationIdFilter() {
        return new WebFilter() {
            private final DeveloperIdentity developerIdentity = new DeveloperIdentity("local", "unknown");

            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                String correlationIdHeader = exchange.getRequest().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);
                final String correlationId = (correlationIdHeader == null || correlationIdHeader.isBlank())
                        ? UUID.randomUUID().toString()
                        : correlationIdHeader;

                String requestIdHeader = exchange.getRequest().getHeaders().getFirst(LoggingConstants.REQUEST_ID_HEADER);
                final String requestId = (requestIdHeader == null || requestIdHeader.isBlank())
                        ? UUID.randomUUID().toString()
                        : requestIdHeader;

                MDCUtility.putCorrelationId(correlationId);
                MDCUtility.putRequestId(requestId);
                developerIdentity.populateMdc();

                exchange.getResponse().getHeaders().add(LoggingConstants.CORRELATION_ID_HEADER, correlationId);
                exchange.getResponse().getHeaders().add(LoggingConstants.REQUEST_ID_HEADER, requestId);

                long startNanos = System.nanoTime();

                return chain.filter(exchange)
                        .doFinally(signal -> {
                            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                            log.info("event=http_request,correlationId={},requestId={},method={},path={},status={},responseTimeMs={}",
                                    correlationId, requestId,
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getPath().value(),
                                    exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0,
                                    elapsedMs);
                            MDCUtility.clearAll();
                        });
            }
        };
    }
}
