package com.claimassist.platform.common_lib.observability;

import com.claimassist.platform.common_lib.observability.event.EventType;

/**
 * Centralized log category and logger name constants.
 *
 * Downstream services should use these constants when emitting logs so that
 * logging remains consistent across the platform and routing rules can be
 * applied based on logger names.
 */
public final class LogCategories {
    private LogCategories() {}

    // Logical categories
    public static final String REQUEST = "REQUEST";
    public static final String BUSINESS = "BUSINESS";
    public static final String SECURITY = "SECURITY";
    public static final String DATABASE = "DATABASE";
    public static final String CACHE = "CACHE";
    public static final String KAFKA = "KAFKA";
    public static final String PERFORMANCE = "PERFORMANCE";
    public static final String EXCEPTION = "EXCEPTION";

    // Logger names (use these in logback to route/format events differently)
    public static final String LOGGER_EVENT = "event.logger";
    public static final String LOGGER_REQUEST = "event.request";
    public static final String LOGGER_BUSINESS = "event.business";
    public static final String LOGGER_SECURITY = "event.security";
    public static final String LOGGER_DATABASE = "event.database";
    public static final String LOGGER_CACHE = "event.cache";
    public static final String LOGGER_KAFKA = "event.kafka";
    public static final String LOGGER_PERFORMANCE = "event.performance";
    public static final String LOGGER_EXCEPTION = "event.exception";

    /**
     * Map an EventType to the recommended logger name.
     */
    public static String loggerNameFor(EventType t) {
        if (t == null) return LOGGER_EVENT;
        switch (t) {
            case REQUEST: return LOGGER_REQUEST;
            case BUSINESS: return LOGGER_BUSINESS;
            case SECURITY: return LOGGER_SECURITY;
            case DATABASE: return LOGGER_DATABASE;
            case CACHE: return LOGGER_CACHE;
            case KAFKA: return LOGGER_KAFKA;
            case PERFORMANCE: return LOGGER_PERFORMANCE;
            case EXCEPTION: return LOGGER_EXCEPTION;
            default: return LOGGER_EVENT;
        }
    }
}

