package com.claimassist.platform.claims_service.entity;

import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "claim_saga_orchestrations", indexes = {
        @Index(name = "idx_claim_saga_status_expires", columnList = "status, expiresAt"),
        @Index(name = "idx_claim_saga_updated", columnList = "updatedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClaimSagaOrchestration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SagaActionType action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SagaOrchestrationStatus status;

    @Enumerated(EnumType.STRING)
    SagaStepType currentStep;

    Long claimId;
    Long policyId;
    String incidentType;
    String incidentDate;
    Long estimatedAmountCents;
    Long actorUserId;
    String note;
    String idempotencyKey;

    @Column(nullable = false)
    int attempts;

    @Column(nullable = false)
    boolean compensationRequired;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    String lastError;

    // Failure recovery tracking
    Instant nextRetryAt;
    Instant lastRecoveryAttemptAt;
    String lastRecoveryError;

    @Column(nullable = false)
    @Builder.Default
    int recoveryFailureCount = 0;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (expiresAt == null) {
            expiresAt = now.plusSeconds(120);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

