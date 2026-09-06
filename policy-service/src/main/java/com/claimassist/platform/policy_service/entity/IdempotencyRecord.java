package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

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

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(nullable = false)
    String operation;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    String responseBody;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    // Deterministic fingerprint of the business request (SHA-256 hex). Nullable for backward compatibility.
    @Column(name = "fingerprint", length = 128)
    String fingerprint;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
