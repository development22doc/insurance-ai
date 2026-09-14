package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "customer_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    Plan plan;

    @Column(nullable = false, unique = true, length = 64)
    String policyNumber;

    @Column(nullable = false, length = 32)
    @Builder.Default
    String status = "PENDING_PAYMENT";

    @Column(nullable = false)
    Instant effectiveDate;

    @Column
    Instant renewalDate;

    @Column
    Instant cancelledAt;

    @Column
    Instant activatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    Instant updatedAt;
}
