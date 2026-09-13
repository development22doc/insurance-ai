package com.claimassist.platform.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class BearerTokenPropagationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (auth instanceof JwtAuthenticationToken jwtAuth) {
                        ServerHttpRequest request = exchange.getRequest();
                        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                            return Mono.just(exchange);
                        }
                        String token = jwtAuth.getToken().getTokenValue();
                        ServerHttpRequest propagatedRequest = request.mutate()
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .build();
                        return Mono.just(exchange.mutate().request(propagatedRequest).build());
                    }
                    return Mono.just(exchange);
                })
                .switchIfEmpty(Mono.just(exchange))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
