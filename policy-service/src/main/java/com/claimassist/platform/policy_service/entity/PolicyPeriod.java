package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "policy_periods",
        indexes = {
                @Index(name = "idx_policy_period_contract_id", columnList = "policy_contract_id"),
                @Index(name = "idx_policy_period_previous_policy_period_id", columnList = "previous_policy_period_id"),
                @Index(name = "idx_policy_period_contract_effective_date", columnList = "policy_contract_id, effective_date"),
                @Index(name = "idx_policy_period_contract_expiration_date", columnList = "policy_contract_id, expiration_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_policy_periods_contract_sequence", columnNames = {"policy_contract_id", "renewal_sequence"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolicyPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    @Column(nullable = false)
    Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_contract_id", nullable = false)
    PolicyContract policyContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_policy_period_id")
    PolicyPeriod previousPolicyPeriod;

    @Column(name = "plan_id", nullable = false)
    Long planId;

    @Column(name = "renewal_sequence", nullable = false)
    Integer renewalSequence;

    @Column(nullable = false, length = 32)
    String status;

    @Column(name = "effective_date", nullable = false)
    Instant effectiveDate;

    @Column(name = "expiration_date", nullable = false)
    Instant expirationDate;

    @Column(name = "renewal_date")
    Instant renewalDate;

    @Column(name = "activated_at")
    Instant activatedAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
