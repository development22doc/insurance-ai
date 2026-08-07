package com.claimassist.platform.common_lib.observability.event;

import com.claimassist.platform.common_lib.observability.LoggingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of EventLogger. Uses a single SLF4J logger name
 * `event.logger` which can be routed independently in logback configuration.
 * All events are emitted as compact JSON strings produced by EventFormatter so
 * consumers can parse them reliably.
 */
public class DefaultEventLogger implements EventLogger {
    private static final Logger eventLogger = LoggerFactory.getLogger("event.logger");

    private final String defaultService;
    private final String defaultApplication;

    public DefaultEventLogger(String service, String application) {
        this.defaultService = service;
        this.defaultApplication = application;
    }

    private void emit(EventType type, String service, String application, Long durationMs, Map<String,Object> details) {
        Map<String,Object> safeDetails = details == null ? new HashMap<>() : new HashMap<>(details);

        EventLog e = EventLog.builder(type)
                .service(service == null ? defaultService : service)
                .application(application == null ? defaultApplication : application)
                .correlationId(MDC.get(LoggingConstants.MDC_CORRELATION_ID))
                .traceId(MDC.get(LoggingConstants.MDC_TRACE_ID))
                .spanId(MDC.get(LoggingConstants.MDC_SPAN_ID))
                .timestamp(Instant.now())
                .durationMs(durationMs)
                .logger("event.logger")
                .details(safeDetails)
                .build();

        String body = EventFormatter.format(e);
        // Choose a logger name based on event type so logback can route them
        String loggerName = com.claimassist.platform.common_lib.observability.LogCategories.loggerNameFor(type);
        Logger logger = LoggerFactory.getLogger(loggerName);

        switch (type) {
            case SECURITY:
            case EXCEPTION:
                logger.error(body);
                break;
            case PERFORMANCE:
                logger.info(body);
                break;
            default:
                logger.info(body);
        }
    }

    @Override
    public void logRequestEvent(String service, String application, long durationMs, Map<String, Object> details) {
        emit(EventType.REQUEST, service, application, durationMs, details);
    }

    @Override
    public void logBusinessEvent(String service, String application, Map<String, Object> details) {
        emit(EventType.BUSINESS, service, application, null, details);
    }

    @Override
    public void logSecurityEvent(String service, String application, Map<String, Object> details) {
        emit(EventType.SECURITY, service, application, null, details);
    }

    @Override
    public void logDatabaseEvent(String service, String application, long durationMs, Map<String, Object> details) {
        emit(EventType.DATABASE, service, application, durationMs, details);
    }

    @Override
    public void logKafkaEvent(String service, String application, Map<String, Object> details) {
        emit(EventType.KAFKA, service, application, null, details);
    }

    @Override
    public void logPerformanceEvent(String service, String application, long durationMs, Map<String, Object> details) {
        emit(EventType.PERFORMANCE, service, application, durationMs, details);
    }

    @Override
    public void logExceptionEvent(String service, String application, Map<String, Object> details) {
        emit(EventType.EXCEPTION, service, application, null, details);
    }
}

