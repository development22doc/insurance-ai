package com.claimassist.platform.agent_service.entity;

import com.claimassist.platform.common_lib.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String aggregateId;

    @Column(nullable = false)
    String eventType;

    @Column(nullable = false)
    String topic;

    String partitionKey;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    Instant createdAt;

    Instant lastAttemptAt;

    @Column(nullable = false)
    Instant nextAttemptAt;

    Instant publishedAt;

    @Builder.Default
    int attempts = 0;

    String lastError;

    // Trace correlation context - stored at event creation time so they can be
    // propagated to Kafka headers when publishing (OutboxEventPublisher runs in
    // a different thread and cannot access the original request's MDC)
    String correlationId;

    String traceId;

    String spanId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }
}
