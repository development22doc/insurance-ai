package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the rate-limit key strategy: authenticated users are keyed by their user id
 * (so one heavy user cannot exhaust the shared quota), while unauthenticated requests fall
 * back to their remote IP (e.g. /auth/signup, /auth/login).
 */
class RateLimiterConfigTest {

    private final EventLogger eventLogger = mock(EventLogger.class);
    private final KeyResolver resolver = new RateLimiterConfig().userKeyResolver(eventLogger);

    private Jwt jwtWith(Object userIdClaim) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-1");
        if (userIdClaim != null) {
            claims.put("userId", userIdClaim);
        }
        return new Jwt("token", Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void authenticatedUserIsKeyedByUserId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/claims/1"));
        Jwt jwt = jwtWith(123L);
        SecurityContextImpl ctx = new SecurityContextImpl(new UsernamePasswordAuthenticationToken(jwt, null));

        StepVerifier.create(resolver.resolve(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .expectNext("user:123")
                .verifyComplete();
    }

    @Test
    void authenticatedUserWithoutUserIdClaimFallsBackToIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 8080)));
        Jwt jwt = jwtWith(null);
        SecurityContextImpl ctx = new SecurityContextImpl(new UsernamePasswordAuthenticationToken(jwt, null));

        StepVerifier.create(resolver.resolve(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .expectNext("ip:10.0.0.7")
                .verifyComplete();
    }

    @Test
    void unauthenticatedRequestIsKeyedByIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/signup")
                .remoteAddress(new InetSocketAddress("192.168.1.5", 8080)));

        String key = resolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:192.168.1.5");
    }

    @Test
    void unauthenticatedRequestWithoutRemoteAddressFallsBackToUnknown() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/signup"));

        String key = resolver.resolve(exchange).block();
        assertThat(key).isEqualTo("ip:unknown");
    }
}