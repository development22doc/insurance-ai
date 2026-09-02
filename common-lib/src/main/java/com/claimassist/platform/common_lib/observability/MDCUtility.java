package com.claimassist.platform.common_lib.observability;

import org.slf4j.MDC;

/**
 * Utility methods for working with SLF4J MDC (Mapped Diagnostic Context).
 * Provides typed helpers to set and clear common keys used by the platform.
 */
public final class MDCUtility {
    private MDCUtility() {}

    public static void putCorrelationId(String id) {
        if (id != null) MDC.put(LoggingConstants.MDC_CORRELATION_ID, id);
    }

    public static void putTraceId(String id) {
        if (id != null) MDC.put(LoggingConstants.MDC_TRACE_ID, id);
    }

    public static void putSpanId(String id) {
        if (id != null) MDC.put(LoggingConstants.MDC_SPAN_ID, id);
    }

    public static void putRequestId(String id) {
        if (id != null) MDC.put(LoggingConstants.MDC_REQUEST_ID, id);
    }

    public static void putDeveloperId(String id) {
        if (id != null) MDC.put(LoggingConstants.MDC_DEVELOPER_ID, id);
    }

    public static void putDeveloperName(String name) {
        if (name != null) MDC.put(LoggingConstants.MDC_DEVELOPER_NAME, name);
    }

    public static void clearAll() {
        MDC.remove(LoggingConstants.MDC_CORRELATION_ID);
        MDC.remove(LoggingConstants.MDC_TRACE_ID);
        MDC.remove(LoggingConstants.MDC_SPAN_ID);
        MDC.remove(LoggingConstants.MDC_REQUEST_ID);
        MDC.remove(LoggingConstants.MDC_DEVELOPER_ID);
        MDC.remove(LoggingConstants.MDC_DEVELOPER_NAME);
    }
}

