package com.claimassist.platform.customer_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "coverage_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CoveragePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    String name; // Basic / Standard / Comprehensive

    @Column(nullable = false)
    String productType; // AUTO / HOME / HEALTH

    @Column(nullable = false)
    Long annualPremiumCents;

    @Column(nullable = false)
    Long deductibleCents;

    @Column(nullable = false)
    Long coverageLimitCents;

    String stripePriceId;
}
