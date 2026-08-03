package com.claimassist.platform.common_lib.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Business-level correlation id (distinct from Zipkin's traceId/spanId) -
 * simple enough to grep out of plain-text logs without needing the tracing
 * backend running. Especially valuable here for tying together every hop of
 * a single customer's "ask the agent about my claim" action, including the
 * asynchronous claim-update saga that may complete seconds later.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info(buildStructuredLog(request, response, correlationId, requestId, elapsedMs));
            MDC.remove(MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String buildStructuredLog(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String correlationId,
                                      String requestId,
                                      long elapsedMs) {
        String traceId = valueOrDash(MDC.get("traceId"));
        String spanId = valueOrDash(MDC.get("spanId"));
        String method = valueOrDash(request.getMethod());
        String path = valueOrDash(request.getRequestURI());
        String query = request.getQueryString() != null ? request.getQueryString() : "";
        String remoteIp = valueOrDash(request.getRemoteAddr());
        String userAgent = maskIfSensitive("User-Agent", request.getHeader("User-Agent"));
        String auth = maskIfSensitive("Authorization", request.getHeader("Authorization"));
        String cookie = maskIfSensitive("Cookie", request.getHeader("Cookie"));

        return "{"
                + "\"event\":\"http_request\","
                + "\"correlationId\":\"" + escape(correlationId) + "\","
                + "\"traceId\":\"" + escape(traceId) + "\","
                + "\"spanId\":\"" + escape(spanId) + "\","
                + "\"requestId\":\"" + escape(requestId) + "\","
                + "\"method\":\"" + escape(method) + "\","
                + "\"path\":\"" + escape(path) + "\","
                + "\"query\":\"" + escape(query) + "\","
                + "\"status\":" + response.getStatus() + ","
                + "\"responseTimeMs\":" + elapsedMs + ","
                + "\"remoteIp\":\"" + escape(remoteIp) + "\","
                + "\"userAgent\":\"" + escape(userAgent) + "\","
                + "\"authorization\":\"" + escape(auth) + "\","
                + "\"cookie\":\"" + escape(cookie) + "\""
                + "}";
    }

    private String maskIfSensitive(String key, String value) {
        if (value == null) {
            return "";
        }
        String normalized = key.toLowerCase();
        if (normalized.contains("authorization") || normalized.contains("cookie") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("password")) {
            return "***";
        }
        return value;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
