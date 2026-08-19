package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientCorrelationFilterTest {

    private ExchangeFunction captureOutbound(AtomicReference<ClientRequest> outbound) {
        return request -> {
            outbound.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
    }

    @Test
    void create_propagatesCorrelationAndTraceHeadersOntoOutboundRequest() throws Exception {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-123");
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-1");
        try {
            WebClientCorrelationFilter filter = new WebClientCorrelationFilter();
            ExchangeFilterFunction ef = (ExchangeFilterFunction) filter.create();

            ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost")).build();
            AtomicReference<ClientRequest> outbound = new AtomicReference<>();

            ef.filter(request, captureOutbound(outbound)).block();

            assertThat(outbound.get().headers().getFirst(LoggingConstants.CORRELATION_ID_HEADER))
                    .isEqualTo("corr-123");
            assertThat(outbound.get().headers().getFirst(LoggingConstants.TRACE_ID_HEADER))
                    .isEqualTo("trace-1");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void create_withoutMdc_addsNoCorrelationHeaders() throws Exception {
        MDC.clear();
        WebClientCorrelationFilter filter = new WebClientCorrelationFilter();
        ExchangeFilterFunction ef = (ExchangeFilterFunction) filter.create();

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost")).build();
        AtomicReference<ClientRequest> outbound = new AtomicReference<>();

        ef.filter(request, captureOutbound(outbound)).block();

        assertThat(outbound.get().headers().getFirst(LoggingConstants.CORRELATION_ID_HEADER)).isNull();
        assertThat(outbound.get().headers().getFirst(LoggingConstants.TRACE_ID_HEADER)).isNull();
    }

    @Test
    void create_proxyHandlesObjectMethodsWithoutError() throws Exception {
        WebClientCorrelationFilter filter = new WebClientCorrelationFilter();
        Object proxy = filter.create();

        assertThat(proxy.toString()).isEqualTo("WebClientCorrelationFilter ExchangeFilterFunction");
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.hashCode()).isNotZero();
    }
}