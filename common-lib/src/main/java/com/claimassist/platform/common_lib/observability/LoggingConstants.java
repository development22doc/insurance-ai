package com.claimassist.platform.common_lib.observability;

/**
 * Centralized string constants used by the observability/logging utilities.
 */
public final class LoggingConstants {
    private LoggingConstants() {}

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    public static final String MDC_DEVELOPER_ID = "developer_id";
    public static final String MDC_DEVELOPER_NAME = "developer_name";
}

