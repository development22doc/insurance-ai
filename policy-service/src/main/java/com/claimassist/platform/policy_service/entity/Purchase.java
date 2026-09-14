package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "purchases",
        indexes = {
                @Index(name = "idx_purchases_customer_id", columnList = "customer_id"),
                @Index(name = "idx_purchases_policy_contract_id", columnList = "policy_contract_id"),
                @Index(name = "idx_purchases_source_policy_period_id", columnList = "source_policy_period_id"),
                @Index(name = "idx_purchases_target_policy_period_id", columnList = "target_policy_period_id"),
                @Index(name = "idx_purchases_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    @Column(nullable = false)
    Long version;

    @Column(nullable = false)
    Long customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_policy_id")
    CustomerPolicy customerPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", length = 32)
    PurchaseType purchaseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_contract_id")
    PolicyContract policyContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_policy_period_id")
    PolicyPeriod sourcePolicyPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_policy_period_id")
    PolicyPeriod targetPolicyPeriod;

    @Column(nullable = false)
    Long amountCents;

    @Column(nullable = false, length = 8)
    String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    PurchaseStatus status;

    @Column(nullable = false, length = 128, unique = true)
    String idempotencyKey;

    @Column(length = 64)
    String provider;

    @Column(length = 128)
    String providerSessionId;

    @Column(length = 128)
    String providerPaymentIntentId;

    @Column(nullable = false)
    Instant initiatedAt;

    @Column
    Instant paidAt;

    @Column
    Instant failedAt;

    @Column
    Instant cancelledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    Instant updatedAt;
}
