package com.claimassist.platform.common_lib.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple helper for performance-related logging so AOP aspects can reuse a common
 * format and threshold behavior.
 */
public final class PerformanceLoggingUtil {
    private static final Logger log = LoggerFactory.getLogger(PerformanceLoggingUtil.class);
    private PerformanceLoggingUtil() {}

    public static void logExecutionTime(String operation, long elapsedMs) {
        log.info("performance event={{\"operation\":\"{}\",\"elapsedMs\":{}}}", operation, elapsedMs);
    }
}

