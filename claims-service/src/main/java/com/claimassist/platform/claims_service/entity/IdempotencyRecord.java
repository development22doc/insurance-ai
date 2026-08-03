package com.claimassist.platform.claims_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Caches the response of a command executed under a client-supplied
 * Idempotency-Key. Critical for claim submission specifically - a browser
 * retry must NEVER create a duplicate claim for the same incident.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdempotencyRecord {

    @Id
    String key;

    @Column(nullable = false)
    Long userId;

    @Column(nullable = false)
    String operation;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    String responseBody;

    @Column(nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
