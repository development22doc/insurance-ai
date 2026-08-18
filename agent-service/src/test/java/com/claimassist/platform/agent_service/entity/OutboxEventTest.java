package com.claimassist.platform.agent_service.entity;

import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void onCreateDefaultsCreatedAtAndNextAttemptAtWhenNull() {
        OutboxEvent event = new OutboxEvent();
        event.onCreate();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isEqualTo(event.getCreatedAt());
    }

    @Test
    void onCreateKeepsExistingCreatedAtAndFillsOnlyMissingNextAttemptAt() {
        OutboxEvent event = new OutboxEvent();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        event.setCreatedAt(createdAt);
        event.onCreate();
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
        assertThat(event.getNextAttemptAt()).isEqualTo(createdAt);
    }

    @Test
    void onCreateKeepsExplicitNextAttemptAt() {
        OutboxEvent event = new OutboxEvent();
        Instant nextAttemptAt = Instant.parse("2026-03-01T00:00:00Z");
        event.setNextAttemptAt(nextAttemptAt);
        event.onCreate();
        assertThat(event.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    }

    @Test
    void builderDefaultsStatusToPending() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId("100")
                .eventType("AgentTurnCompleted")
                .topic("agent-events")
                .payload("{}")
                .createdAt(Instant.now())
                .nextAttemptAt(Instant.now())
                .build();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
    }
}