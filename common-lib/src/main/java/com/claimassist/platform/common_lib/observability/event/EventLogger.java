package com.claimassist.platform.common_lib.observability.event;

import java.util.Map;

/**
 * Public API for emitting structured observability events. Implementations must
 * ensure canonical fields are present and must not leak secrets.
 */
public interface EventLogger {
    void logRequestEvent(String service, String application, long durationMs, Map<String,Object> details);
    void logBusinessEvent(String service, String application, Map<String,Object> details);
    void logSecurityEvent(String service, String application, Map<String,Object> details);
    void logDatabaseEvent(String service, String application, long durationMs, Map<String,Object> details);
    void logKafkaEvent(String service, String application, Map<String,Object> details);
    void logPerformanceEvent(String service, String application, long durationMs, Map<String,Object> details);
    void logExceptionEvent(String service, String application, Map<String,Object> details);
}

