package com.claimassist.platform.common_lib.observability;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor that propagates correlation id and trace id to outbound calls.
 */
public class RestTemplateCorrelationInterceptor implements ClientHttpRequestInterceptor {
    public RestTemplateCorrelationInterceptor() {}

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String correlation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
        if (correlation != null) {
            request.getHeaders().set(LoggingConstants.CORRELATION_ID_HEADER, correlation);
        }
        String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
        if (traceId != null) request.getHeaders().set(LoggingConstants.TRACE_ID_HEADER, traceId);
        return execution.execute(request, body);
    }
}

