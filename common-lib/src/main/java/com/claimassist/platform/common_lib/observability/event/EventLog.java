package com.claimassist.platform.common_lib.observability.event;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable representation of an observability event. This POJO is intentionally
 * generic so downstream services can populate arbitrary metadata in the
 * `details` map while the EventLogger ensures the canonical fields are set.
 */
public final class EventLog {
    private final EventType eventType;
    private final String service;
    private final String application;
    private final String correlationId;
    private final String traceId;
    private final String spanId;
    private final Instant timestamp;
    private final Long durationMs; // nullable
    private final String logger;
    private final Map<String, Object> details;

    private EventLog(Builder b) {
        this.eventType = b.eventType;
        this.service = b.service;
        this.application = b.application;
        this.correlationId = b.correlationId;
        this.traceId = b.traceId;
        this.spanId = b.spanId;
        this.timestamp = b.timestamp == null ? Instant.now() : b.timestamp;
        this.durationMs = b.durationMs;
        this.logger = b.logger;
        this.details = b.details == null ? Collections.emptyMap() : Collections.unmodifiableMap(b.details);
    }

    public EventType getEventType() { return eventType; }
    public String getService() { return service; }
    public String getApplication() { return application; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public Instant getTimestamp() { return timestamp; }
    public Long getDurationMs() { return durationMs; }
    public String getLogger() { return logger; }
    public Map<String, Object> getDetails() { return details; }

    public static Builder builder(EventType type) { return new Builder(type); }

    public static final class Builder {
        private final EventType eventType;
        private String service;
        private String application;
        private String correlationId;
        private String traceId;
        private String spanId;
        private Instant timestamp;
        private Long durationMs;
        private String logger;
        private Map<String, Object> details;

        public Builder(EventType eventType) { this.eventType = eventType; }

        public Builder service(String s) { this.service = s; return this; }
        public Builder application(String a) { this.application = a; return this; }
        public Builder correlationId(String c) { this.correlationId = c; return this; }
        public Builder traceId(String t) { this.traceId = t; return this; }
        public Builder spanId(String s) { this.spanId = s; return this; }
        public Builder timestamp(Instant ts) { this.timestamp = ts; return this; }
        public Builder durationMs(Long d) { this.durationMs = d; return this; }
        public Builder logger(String l) { this.logger = l; return this; }
        public Builder details(Map<String, Object> m) { this.details = m; return this; }

        public EventLog build() { return new EventLog(this); }
    }
}

