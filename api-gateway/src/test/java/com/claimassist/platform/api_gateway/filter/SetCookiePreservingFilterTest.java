package com.claimassist.platform.api_gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SetCookiePreservingFilterTest {

    private final SetCookiePreservingFilter filter = new SetCookiePreservingFilter();

    @Test
    void splitsCombinedSetCookie_whenExpiresContainsComma() {
        // Simulate downstream returning a single Set-Cookie header containing two cookies
        // where the first cookie has an Expires value containing commas.
        String access = "CLAIMASSIST_ACCESS_TOKEN=access-val; Path=/; HttpOnly; Secure; Expires=Wed, 09 Sep 2026 12:34:56 GMT";
        String refresh = "CLAIMASSIST_REFRESH_TOKEN=refresh-val; Path=/; HttpOnly; Secure; Max-Age=604800";
        String combined = access + ", " + refresh;

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        // Chain that writes the single combined header then completes (committing the response)
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = e -> {
            e.getResponse().getHeaders().set(HttpHeaders.SET_COOKIE, combined);
            return e.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        List<String> result = exchange.getResponse().getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(result).isNotNull();
        // Two separate Set-Cookie header entries expected
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0)).contains("CLAIMASSIST_ACCESS_TOKEN=");
        assertThat(result.get(1)).contains("CLAIMASSIST_REFRESH_TOKEN=");
    }

    @Test
    void leavesMultipleSetCookieHeadersIntact() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        String accessHeader = "CLAIMASSIST_ACCESS_TOKEN=access-val; Path=/; HttpOnly; Secure; Max-Age=3600";
        String refreshHeader = "CLAIMASSIST_REFRESH_TOKEN=refresh-val; Path=/; HttpOnly; Secure; Max-Age=604800";

        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = e -> {
            e.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, accessHeader);
            e.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, refreshHeader);
            return e.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        List<String> result = exchange.getResponse().getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactly(accessHeader, refreshHeader);
    }
}

