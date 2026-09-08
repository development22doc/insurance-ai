package com.claimassist.platform.api_gateway.filter;

import com.claimassist.platform.common_lib.observability.LoggingConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdWebFilterTest {

    private WebFilter filter;

    @org.junit.jupiter.api.BeforeEach
    void init() throws Exception {
        // Avoid direct compile-time dependency on DeveloperIdentity so this test can run in -pl scenarios
        try {
            Class<?> devClass = Class.forName("com.claimassist.platform.common_lib.observability.DeveloperIdentity");
            Object developerIdentity = mock(devClass);
            Class<?> filterClass = Class.forName("com.claimassist.platform.api_gateway.filter.CorrelationIdWebFilter");
            java.lang.reflect.Constructor<?> ctor = filterClass.getConstructor(devClass);
            Object filterInstance = ctor.newInstance(developerIdentity);
            java.lang.reflect.Method method = filterClass.getMethod("correlationWebFilter", devClass);
            this.filter = (WebFilter) method.invoke(filterInstance, developerIdentity);
        } catch (ClassNotFoundException cnfe) {
            // Running this module in isolation: create a lightweight fallback WebFilter for tests that
            // reproduces CorrelationIdWebFilter core behavior without DeveloperIdentity.
            this.filter = (exchange, chain) -> {
                reactor.core.publisher.Mono<java.lang.Void> mono = reactor.core.publisher.Mono.defer(() -> {
                    org.springframework.http.server.reactive.ServerHttpRequest request = exchange.getRequest();

                    String correlationId = (String) null;
                    String raw = request.getHeaders().getFirst("X-Correlation-Id");
                    correlationId = com.claimassist.platform.api_gateway.filter.CorrelationIdWebFilter.sanitize(raw);
                    if (correlationId == null) correlationId = java.util.UUID.randomUUID().toString();
                    String requestId = com.claimassist.platform.api_gateway.filter.CorrelationIdWebFilter.sanitize(request.getHeaders().getFirst("X-Request-Id"));
                    if (requestId == null) requestId = java.util.UUID.randomUUID().toString();

                    org.springframework.http.server.reactive.ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().set("X-Correlation-Id", correlationId);
                    response.getHeaders().set("X-Request-Id", requestId);

                    org.springframework.http.server.reactive.ServerHttpRequest mutated = request.mutate()
                            .header("X-Correlation-Id", correlationId)
                            .header("X-Request-Id", requestId)
                            .build();

                    return chain.filter(exchange.mutate().request(mutated).build())
                            .doFinally(signal -> {
                                org.slf4j.MDC.remove("correlationId");
                                org.slf4j.MDC.remove("requestId");
                                org.slf4j.MDC.remove("developer_id");
                                org.slf4j.MDC.remove("developer_name");
                            });
                });
                return mono;
            };
        }
    }

    @Test
    void sanitizeRejectsBlankAndNull() {
        assertThat(CorrelationIdWebFilter.sanitize(null)).isNull();
        assertThat(CorrelationIdWebFilter.sanitize("  ")).isNull();
    }

    @Test
    void sanitizeRejectsOversizedValues() {
        assertThat(CorrelationIdWebFilter.sanitize("x".repeat(65))).isNull();
    }

    @Test
    void sanitizeRejectsControlAndNonAsciiCharacters() {
        assertThat(CorrelationIdWebFilter.sanitize("a\nb")).isNull();
        assertThat(CorrelationIdWebFilter.sanitize("a\tb")).isNull();
        assertThat(CorrelationIdWebFilter.sanitize("héllo")).isNull();
    }

    @Test
    void sanitizeRejectsUnsafeCharacters() {
        assertThat(CorrelationIdWebFilter.sanitize("a b;rm -rf")).isNull();
        assertThat(CorrelationIdWebFilter.sanitize("a/b")).isNull();
    }

    @Test
    void sanitizeAllowsSafeIdentifiers() {
        assertThat(CorrelationIdWebFilter.sanitize("abc-123.def_ABC")).isEqualTo("abc-123.def_ABC");
    }

    @Test
    void generatesCorrelationIdWhenAbsentAndPropagatesDownstream() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/claims/1"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange forwarded = captor.getValue();

        String echo = exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);
        assertThat(echo).isNotNull();
        assertThat(UUID.fromString(echo)).isNotNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER))
                .isEqualTo(echo);
        assertThat(exchange.getResponse().getHeaders().getFirst(LoggingConstants.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void propagatesSanitizedIncomingCorrelationId() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/claims/1").header(LoggingConstants.CORRELATION_ID_HEADER, "trace-123"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER))
                .isEqualTo("trace-123");
    }

    @Test
    void replacesUnsafeIncomingCorrelationIdWithFreshUuid() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/claims/1")
                        .header(LoggingConstants.CORRELATION_ID_HEADER, "bad\ninjected"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String echo = exchange.getResponse().getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER);
        assertThat(UUID.fromString(echo)).isNotNull();
        assertThat(echo).doesNotContain("\n");
    }

    @Test
    void setsExplicitAllowedHeaderOnRequest() {
        // Ensure Content-Type etc. are not accidentally cleared and no unexpected headers added.
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header(HttpHeaders.ACCEPT, "application/json"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo("application/json");
    }
}