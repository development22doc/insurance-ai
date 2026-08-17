package com.claimassist.platform.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewaySecurityHeadersFilterTest {

    private final WebFilter filter = new GatewaySecurityHeadersFilter().securityHeadersWebFilter();

    @Test
    void addsSecurityHeadersToResponse() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/claims/1"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(headers.getFirst("Cache-Control")).contains("no-store");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void doesNotAddHeadersToCommittedResponse() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            ServerHttpResponse response = ex.getResponse();
            response.getHeaders().set("X-Content-Type-Options", "already-set");
            response.setComplete();
            return response.setComplete();
        });

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }
}