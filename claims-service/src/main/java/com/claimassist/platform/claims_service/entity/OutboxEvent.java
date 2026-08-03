package com.claimassist.platform.claims_service.entity;

import com.claimassist.platform.common_lib.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Transactional Outbox row - written in the SAME local transaction as the
 * domain change it describes (e.g. "we applied a claim status change because
 * the agent's saga request passed validation"), so the DB write and the
 * "intent to publish a Kafka event" are atomic. See OutboxEventPublisher.
 */
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
