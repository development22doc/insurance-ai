package com.claimassist.platform.common_lib.observability;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
    // Compatibility constants: some other classes reference these static fields.
    public static final String CORRELATION_ID_HEADER = LoggingConstants.CORRELATION_ID_HEADER;
    public static final String MDC_KEY = LoggingConstants.MDC_CORRELATION_ID;
    public static final String REQUEST_ID_HEADER = LoggingConstants.REQUEST_ID_HEADER;
    public static final String REQUEST_ID_MDC_KEY = LoggingConstants.MDC_REQUEST_ID;

    // CorrelationIdFilter intentionally avoids a hard compile-time dependency on
    // micrometer Tracer. Many tracing implementations automatically populate
    // MDC with traceId/spanId; this filter focuses on correlation/request ids.

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(LoggingConstants.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String requestId = request.getHeader(LoggingConstants.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDCUtility.putCorrelationId(correlationId);
        MDCUtility.putRequestId(requestId);
        response.setHeader(LoggingConstants.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(LoggingConstants.REQUEST_ID_HEADER, requestId);

        // Ensure any trace/span present in MDC (populated by tracing instrumentation)
        // are echoed back in response headers so callers can reference them.
        String traceId = org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID);
        String spanId = org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID);
        if (traceId != null) response.setHeader(LoggingConstants.TRACE_ID_HEADER, traceId);
        if (spanId != null) response.setHeader(LoggingConstants.SPAN_ID_HEADER, spanId);

        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info(buildStructuredLog(request, response, correlationId, requestId, elapsedMs));
            MDCUtility.clearAll();
        }
    }

    private String buildStructuredLog(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String correlationId,
                                      String requestId,
                                      long elapsedMs) {
        String traceId = valueOrDash(MDCUtilityRead("traceId"));
        String spanId = valueOrDash(MDCUtilityRead("spanId"));
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
        String normalizedKey = key == null ? "" : key.toLowerCase();
        String normalizedValue = value == null ? "" : value.trim();

        // If header name indicates sensitive content, mask generically
        if (normalizedKey.contains("authorization") || normalizedKey.contains("password")
                || normalizedKey.contains("secret") || normalizedKey.contains("client")
                || normalizedKey.contains("api") || normalizedKey.contains("token")
                || normalizedKey.contains("refresh") || normalizedKey.contains("access")) {
            // For Authorization header, preserve scheme (e.g., Bearer) but mask token
            if (normalizedKey.contains("authorization") && normalizedValue.contains(" ")) {
                String scheme = normalizedValue.substring(0, normalizedValue.indexOf(' '));
                return scheme + " ***";
            }
            return "***";
        }

        // Mask values that look like JWTs or long tokens even if header name isn't sensitive
        if (looksLikeJwt(normalizedValue) || looksLikeLongToken(normalizedValue)) {
            // If it looks like 'Bearer <token>' mask token part
            if (normalizedValue.toLowerCase().startsWith("bearer ")) {
                return "Bearer ***";
            }
            return "***";
        }

        return value;
    }

    private boolean looksLikeJwt(String v) {
        // crude check: JWTs have three parts separated by dots
        return v != null && v.chars().filter(ch -> ch == '.').count() == 2;
    }

    private boolean looksLikeLongToken(String v) {
        // treat long base64-like strings as tokens
        return v != null && v.length() > 40;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // helper to read MDC values using the existing utility without exposing MDC
    private String MDCUtilityRead(String key) {
        // try the known constants
        switch (key) {
            case "traceId":
                return org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID);
            case "spanId":
                return org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID);
            default:
                return org.slf4j.MDC.get(key);
        }
    }
}
