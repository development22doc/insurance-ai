package com.claimassist.platform.common_lib.observability;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign RequestInterceptor that adds correlation and trace headers to outgoing requests.
 */
public class FeignCorrelationRequestInterceptor implements RequestInterceptor {
    public FeignCorrelationRequestInterceptor() {}

    @Override
    public void apply(RequestTemplate template) {
        String correlation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
        if (correlation != null) template.header(LoggingConstants.CORRELATION_ID_HEADER, correlation);
        String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
        if (traceId != null) template.header(LoggingConstants.TRACE_ID_HEADER, traceId);
    }
}

