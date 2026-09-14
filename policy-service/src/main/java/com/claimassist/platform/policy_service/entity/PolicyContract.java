package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "policy_contracts",
        indexes = {
                @Index(name = "idx_policy_contracts_customer_id", columnList = "customer_id"),
                @Index(name = "idx_policy_contracts_current_policy_period_id", columnList = "current_policy_period_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolicyContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    @Column(nullable = false)
    Long version;

    @Column(name = "customer_id", nullable = false)
    Long customerId;

    @Column(name = "product_id", nullable = false)
    Long productId;

    @Column(name = "policy_number", nullable = false, unique = true, length = 64)
    String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_policy_period_id")
    PolicyPeriod currentPolicyPeriod;

    @Column(nullable = false, length = 32)
    String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
