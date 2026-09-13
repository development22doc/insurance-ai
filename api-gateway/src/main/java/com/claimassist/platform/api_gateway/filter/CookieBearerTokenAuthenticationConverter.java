package com.claimassist.platform.api_gateway.filter;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class CookieBearerTokenAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "CLAIMASSIST_ACCESS_TOKEN";

    private final ServerAuthenticationConverter delegate =
            new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return delegate.convert(exchange)
                .switchIfEmpty(Mono.defer(() -> {
                    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE_NAME);
                    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
                        return Mono.empty();
                    }
                    return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
                }));
    }
}
