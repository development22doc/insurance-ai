package com.claimassist.platform.common_lib.observability;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

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
        // Prefer MDC values; if those are blank, attempt to obtain trace/span from
        // Micrometer Tracer (if available) by doing a runtime reflection lookup
        // against the Spring WebApplicationContext. This avoids a hard compile-time
        // dependency on micrometer while allowing CorrelationIdFilter to report
        // tracing identifiers when they exist.
        String traceId = valueOrDash(resolveTraceId(request));
        String spanId = valueOrDash(resolveSpanId(request));
        String method = valueOrDash(request.getMethod());
        String path = valueOrDash(request.getRequestURI());
        // Query strings can carry one-time OAuth authorization codes, refresh or
        // access tokens or other credentials (e.g. /customer/auth/callback?code=...).
        // Never write those raw into the observability log - redact sensitive keys.
        String query = maskQuery(request.getQueryString());
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

    /**
     * Redact credential-bearing query parameters so observability/audit logs do
     * not become a secret leak. OAuth flows pass short-lived but sensitive values
     * in the query string (code, state, id_token, access/refresh tokens). Keep the
     * structure (key=***) so correlation still works, never the value.
     */
    private String maskQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query == null ? "" : query;
        }
        String[] params = query.split("&");
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) masked.append('&');
            String param = params[i];
            int eq = param.indexOf('=');
            String key = eq >= 0 ? param.substring(0, eq) : param;
            if (isSensitiveQueryKey(key)) {
                masked.append(key).append('=').append("***");
            } else {
                masked.append(param);
            }
        }
        return masked.toString();
    }

    private boolean isSensitiveQueryKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase();
        return k.contains("token") || k.contains("secret") || k.contains("password")
                || k.contains("passwd") || k.contains("pwd") || k.contains("authorization")
                || k.contains("credential") || k.contains("api_key") || k.contains("apikey")
                || k.contains("client_secret") || k.contains("access_token")
                || k.contains("refresh_token") || k.contains("code_verifier")
                || k.contains("code_challenge") || k.equals("code") || k.equals("auth_code")
                || k.contains("auth_code");
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

    // Attempt to resolve traceId using available tracer when MDC is blank.
    private String resolveTraceId(HttpServletRequest request) {
        String fromMdc = org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID);
        if (fromMdc != null && !fromMdc.isBlank()) return fromMdc;

        try {
            WebApplicationContext ctx = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
            if (ctx == null) return null;

            // Lookup tracer bean by type using reflection to avoid compile-time dependency
            Class<?> tracerClass = Class.forName("io.micrometer.tracing.Tracer");
            Object tracer = null;
            try {
                tracer = ctx.getBean(tracerClass);
            } catch (Exception ignored) {
                // no tracer bean available
                return null;
            }

            if (tracer == null) return null;

            Object currentSpan = tracer.getClass().getMethod("currentSpan").invoke(tracer);
            if (currentSpan == null) return null;
            Object spanContext = currentSpan.getClass().getMethod("context").invoke(currentSpan);
            if (spanContext == null) return null;
            Object traceId = spanContext.getClass().getMethod("traceId").invoke(spanContext);
            return traceId == null ? null : traceId.toString();
        } catch (ClassNotFoundException cnfe) {
            // Micrometer tracing not on classpath
            return null;
        } catch (Throwable t) {
            // Any reflection or bean lookup issue - do not fail the request flow
            log.debug("Unable to resolve traceId from tracer: {}", t.toString());
            return null;
        }
    }

    private String resolveSpanId(HttpServletRequest request) {
        String fromMdc = org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID);
        if (fromMdc != null && !fromMdc.isBlank()) return fromMdc;

        try {
            WebApplicationContext ctx = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
            if (ctx == null) return null;

            Class<?> tracerClass = Class.forName("io.micrometer.tracing.Tracer");
            Object tracer = null;
            try {
                tracer = ctx.getBean(tracerClass);
            } catch (Exception ignored) {
                return null;
            }

            if (tracer == null) return null;

            Object currentSpan = tracer.getClass().getMethod("currentSpan").invoke(tracer);
            if (currentSpan == null) return null;
            Object spanContext = currentSpan.getClass().getMethod("context").invoke(currentSpan);
            if (spanContext == null) return null;
            Object spanId = spanContext.getClass().getMethod("spanId").invoke(spanContext);
            return spanId == null ? null : spanId.toString();
        } catch (ClassNotFoundException cnfe) {
            return null;
        } catch (Throwable t) {
            log.debug("Unable to resolve spanId from tracer: {}", t.toString());
            return null;
        }
    }
}
