package com.claimassist.platform.common_lib.observability.event;

import com.claimassist.platform.common_lib.observability.LogCategories;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventLogTest {

    @Test
    void builder_setsAllFields() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> details = Map.of("k", "v");

        EventLog e = EventLog.builder(EventType.DATABASE)
                .service("s")
                .application("a")
                .correlationId("c")
                .traceId("t")
                .spanId("sp")
                .timestamp(ts)
                .durationMs(5L)
                .logger("l")
                .details(details)
                .build();

        assertThat(e.getEventType()).isEqualTo(EventType.DATABASE);
        assertThat(e.getService()).isEqualTo("s");
        assertThat(e.getApplication()).isEqualTo("a");
        assertThat(e.getCorrelationId()).isEqualTo("c");
        assertThat(e.getTraceId()).isEqualTo("t");
        assertThat(e.getSpanId()).isEqualTo("sp");
        assertThat(e.getTimestamp()).isEqualTo(ts);
        assertThat(e.getDurationMs()).isEqualTo(5L);
        assertThat(e.getLogger()).isEqualTo("l");
        assertThat(e.getDetails()).containsEntry("k", "v");
    }

    @Test
    void nullTimestamp_defaultsToNow() {
        EventLog e = EventLog.builder(EventType.REQUEST).build();

        assertThat(e.getTimestamp()).isNotNull();
    }

    @Test
    void nullDetails_defaultsToEmptyUnmodifiable() {
        EventLog e = EventLog.builder(EventType.BUSINESS).build();

        assertThat(e.getDetails()).isEmpty();
        assertThatThrownBy(() -> e.getDetails().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void providedDetails_areUnmodifiable() {
        EventLog e = EventLog.builder(EventType.BUSINESS)
                .details(Collections.singletonMap("a", "b"))
                .build();

        assertThatThrownBy(() -> e.getDetails().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}