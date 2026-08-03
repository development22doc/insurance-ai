package com.claimassist.platform.customer_service.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_plan_id", nullable = false)
    CoveragePlan coveragePlan;

    @Column(nullable = false, unique = true)
    String policyNumber;

    @Column(nullable = false)
    @Builder.Default
    String status = "PENDING"; // ACTIVE / LAPSED / CANCELLED / PENDING_RENEWAL

    @Column(nullable = false)
    Instant effectiveDate;

    Instant renewalDate;

    String stripeSubscriptionId;
}
