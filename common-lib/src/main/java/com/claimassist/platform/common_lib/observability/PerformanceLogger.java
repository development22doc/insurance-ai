package com.claimassist.platform.common_lib.observability;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.event.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Central performance logger that classifies durations into INFO/WARN/ERROR
 * based on thresholds. It is generic and can be used by AOP aspects and
 * instrumentation wrappers.
 */
public class PerformanceLogger {
    private final PerformanceLoggingProperties props;
    private final EventLogger eventLogger; // optional

    public PerformanceLogger(PerformanceLoggingProperties props, EventLogger eventLogger) {
        this.props = props;
        this.eventLogger = eventLogger;
    }

    public void log(String category, String operation, long durationMs, Map<String,Object> details) {
        // Determine thresholds: look for category-specific override
        PerformanceLoggingProperties.Thresholds t = props.getCategories().get(category);
        long info = t != null ? t.getInfo() : props.getInfoThresholdMs();
        long warn = t != null ? t.getWarn() : props.getWarnThresholdMs();
        long error = t != null ? t.getError() : props.getErrorThresholdMs();

        String loggerName = LogCategories.loggerNameFor(mapCategoryToEventType(category));
        Logger logger = LoggerFactory.getLogger(loggerName);

        String message = buildMessage(category, operation, durationMs, details);

        if (durationMs >= error) {
            logger.error(message);
        } else if (durationMs >= warn) {
            logger.warn(message);
        } else if (durationMs >= info) {
            logger.info(message);
        } else {
            logger.debug(message);
        }

        // Also emit an EventLogger performance event if available
        if (eventLogger != null) {
            eventLogger.logPerformanceEvent(null, null, durationMs, details);
        }
    }

    private String buildMessage(String category, String operation, long durationMs, Map<String,Object> details) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"category\":\"").append(LoggingHelper.escapeJson(category)).append('\"');
        sb.append(',');
        sb.append("\"operation\":\"").append(LoggingHelper.escapeJson(operation)).append('\"');
        sb.append(',');
        sb.append("\"durationMs\":").append(durationMs);
        if (details != null && !details.isEmpty()) {
            sb.append(',').append("\"details\":{");
            boolean first = true;
            for (Map.Entry<String,Object> en : details.entrySet()) {
                if (!first) sb.append(','); first = false;
                sb.append('"').append(LoggingHelper.escapeJson(en.getKey())).append('"').append(':');
                Object v = en.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v.toString());
                else sb.append('"').append(LoggingHelper.escapeJson(String.valueOf(v))).append('"');
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private EventType mapCategoryToEventType(String category) {
        if (category == null) return EventType.PERFORMANCE;
        switch (category.toUpperCase()) {
            case "REQUEST": return EventType.REQUEST;
            case "BUSINESS": return EventType.BUSINESS;
            case "SECURITY": return EventType.SECURITY;
            case "DATABASE": return EventType.DATABASE;
            case "CACHE": return EventType.CACHE;
            case "KAFKA": return EventType.KAFKA;
            case "EXCEPTION": return EventType.EXCEPTION;
            default: return EventType.PERFORMANCE;
        }
    }
}

