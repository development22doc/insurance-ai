package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized exception logging helper. Keeps sensitive information out of
 * logs and emits structured messages.
 */
public final class ExceptionLoggingUtil {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingUtil.class);

    private ExceptionLoggingUtil() {}

    public static void logException(JoinPoint jp, Throwable ex) {
        String location = jp.getSignature().toShortString();
        String correlation = org.slf4j.MDC.get(LoggingConstants.MDC_CORRELATION_ID);
        String trace = org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID);
        String span = org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID);
        log.error("exception event={{\"location\":\"{}\",\"correlationId\":\"{}\",\"traceId\":\"{}\",\"spanId\":\"{}\"}} - {}", location, correlation, trace, span, sanitizeMessage(ex.getMessage()), ex);
    }

    private static String sanitizeMessage(String m) {
        if (m == null) return "";
        // Never include tokens or authorization headers in exception messages
        return m.replaceAll("(?i)(authorization|token|password|secret)[:=][^\\s,;]+", "$1=***");
    }
}

