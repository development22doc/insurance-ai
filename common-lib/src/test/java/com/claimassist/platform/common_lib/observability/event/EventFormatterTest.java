package com.claimassist.platform.common_lib.observability.event;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventFormatterTest {

    @Test
    void format_includesCanonicalFields() {
        EventLog e = EventLog.builder(EventType.BUSINESS)
                .service("customer-service")
                .application("customer-service")
                .correlationId("corr-1")
                .traceId("trace-1")
                .spanId("span-1")
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .durationMs(123L)
                .logger("event.logger")
                .details(Map.of("key", "value"))
                .build();

        String json = EventFormatter.format(e);

        assertThat(json).contains("\"eventType\":\"BUSINESS\"");
        assertThat(json).contains("\"service\":\"customer-service\"");
        assertThat(json).contains("\"correlationId\":\"corr-1\"");
        assertThat(json).contains("\"timestamp\":\"2026-01-01T00:00:00Z\"");
        assertThat(json).contains("\"duration\":123");
        assertThat(json).contains("\"key\":\"value\"");
    }

    @Test
    void format_detailsSupportsNullAndNumber() {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("nullV", null);
        details.put("numV", 42);
        EventLog e = EventLog.builder(EventType.CACHE)
                .service("s").application("a")
                .details(details)
                .build();

        String json = EventFormatter.format(e);

        assertThat(json).contains("\"nullV\":null").contains("\"numV\":42");
    }

    @Test
    void format_blankCorrelationIdFallsBackToMdc() {
        try {
            MDC.put("correlationId", "from-mdc");
            EventLog e = EventLog.builder(EventType.REQUEST)
                    .service("s").application("a")
                    .correlationId(" ")
                    .build();

            String json = EventFormatter.format(e);

            assertThat(json).contains("\"correlationId\":\"from-mdc\"");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void format_noDetails_noDurationField() {
        EventLog e = EventLog.builder(EventType.KAFKA).service("s").application("a").build();

        String json = EventFormatter.format(e);

        assertThat(json).doesNotContain("duration");
        assertThat(json).doesNotContain("details");
    }
}