package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestTemplateCorrelationInterceptorTest {

    private final RestTemplateCorrelationInterceptor interceptor = new RestTemplateCorrelationInterceptor();

    @Test
    void intercept_propagatesCorrelationAndTraceHeaders() throws IOException {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-1");
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-1");
        try {
            HttpRequest request = mock(HttpRequest.class);
            HttpHeaders headers = new HttpHeaders();
            when(request.getHeaders()).thenReturn(headers);
            ClientHttpResponse response = mock(ClientHttpResponse.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            when(execution.execute(request, new byte[0])).thenReturn(response);

            ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

            assertThat(result).isSameAs(response);
            assertThat(headers.getFirst(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo("corr-1");
            assertThat(headers.getFirst(LoggingConstants.TRACE_ID_HEADER)).isEqualTo("trace-1");
            verify(execution).execute(request, new byte[0]);
        } finally {
            MDC.clear();
        }
    }

    @Test
    void intercept_noMdc_skipsHeadersButStillExecutes() throws IOException {
        MDC.clear();
        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(request, new byte[0])).thenReturn(response);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result).isSameAs(response);
        assertThat(headers).isEmpty();
        verify(execution).execute(request, new byte[0]);
    }
}