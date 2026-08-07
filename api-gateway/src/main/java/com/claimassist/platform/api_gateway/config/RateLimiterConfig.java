package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Redis-backed so the limit holds across however many api-gateway replicas
 * are running. Keyed by authenticated user id where possible (so one heavy
 * user can't exhaust the shared quota), falling back to remote IP for
 * unauthenticated requests - i.e. exactly /auth/signup and /auth/login, the
 * endpoints most worth protecting against credential-stuffing.
 * <p>
 * Also protects the LLM budget specifically: /agent/stream calls cost real
 * money per token at the model provider - rate limiting this endpoint is not
 * just abuse prevention, it's cost control.
 * <p>
 * User id resolution used to go through JwtGatewayService's own hand-rolled
 * HMAC decode; now that GatewaySecurityConfig runs a real reactive OAuth2
 * Resource Server ahead of routing, the validated Keycloak Jwt is already
 * sitting on the reactive SecurityContext by the time this KeyResolver runs -
 * so this just reads it from there instead of re-parsing the token itself.
 */
@Configuration
@Slf4j
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver(EventLogger eventLogger) {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt jwt) {
                        Object userId = jwt.getClaim("userId");
                        if (userId != null) {
                            return Mono.just("user:" + userId);
                        }
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown";
                    return "ip:" + remoteAddress;
                }));
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {

        return new RedisRateLimiter(20, 40, 1);
    }
}
