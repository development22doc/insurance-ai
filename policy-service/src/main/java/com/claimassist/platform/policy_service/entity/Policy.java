package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "policy_number", nullable = false, unique = true)
    String policyNumber;

    @Column(name = "customer_id", nullable = false)
    Long customerId; // plain value only - no FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_plan_id", nullable = false)
    Plan coveragePlan;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "effective_date")
    Instant effectiveDate;

    @Column(name = "renewal_date")
    Instant renewalDate;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Version
    @Column(name = "version")
    Long version;

    @Column(name = "stripe_payment_intent_id", unique = true)
    String stripePaymentIntentId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /**
     * Validates that the given status is a valid lifecycle state.
     * Used by business logic before persisting policy status changes.
     */
    public boolean isValidStatus(String status) {
        if (status == null) return false;
        return switch (status) {
            case "DRAFT", "PENDING_PAYMENT", "ACTIVE", "CANCELLED", "EXPIRED", "REINSTATEMENT_PENDING" -> true;
            default -> false;
        };
    }

    /**
     * Domain-level lifecycle transition checks. Centralizes allowed transitions.
     * Same-state transitions are allowed as no-ops.
     */
    public boolean canTransitionTo(LifecycleStatus target) {
        if (target == null) return false;
        LifecycleStatus current;
        try {
            current = LifecycleStatus.valueOf(this.status);
        } catch (Exception e) {
            return false;
        }
        if (current == target) return true; // idempotent

        return switch (current) {
            case DRAFT -> target == LifecycleStatus.PENDING_PAYMENT;
            case PENDING_PAYMENT -> target == LifecycleStatus.ACTIVE || target == LifecycleStatus.CANCELLED;
            case ACTIVE -> target == LifecycleStatus.CANCELLED || target == LifecycleStatus.EXPIRED;
            case CANCELLED -> target == LifecycleStatus.REINSTATEMENT_PENDING;
            case EXPIRED -> target == LifecycleStatus.REINSTATEMENT_PENDING;
            case REINSTATEMENT_PENDING -> target == LifecycleStatus.ACTIVE;
        };
    }

    /**
     * Perform a lifecycle transition with validation. Throws IllegalArgumentException if invalid.
     */
    public void transitionTo(LifecycleStatus target) {
        if (target == null) throw new IllegalArgumentException("Target lifecycle status required");
        if (!canTransitionTo(target)) {
            throw new IllegalArgumentException("Invalid lifecycle transition from " + this.status + " to " + target.name());
        }
        this.status = target.name();
    }
}
