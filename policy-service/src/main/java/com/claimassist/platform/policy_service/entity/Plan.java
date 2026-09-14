package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(nullable = false, unique = true, length = 64)
    String code;

    @Column(nullable = false, length = 128)
    String name;

    @Column(nullable = false, length = 32)
    String status;

    @Column(nullable = false)
    Long annualPremiumCents;

    @Column(nullable = false)
    Long deductibleCents;

    @Column(nullable = false)
    Long coverageLimitCents;

    @Column(length = 8)
    String currency;

    @Column(length = 128)
    String stripePriceId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    Instant updatedAt;
}
