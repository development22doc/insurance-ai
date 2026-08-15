package com.claimassist.platform.claims_service.entity;

import com.claimassist.platform.common_lib.enums.ClaimStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String claimNumber;

    /**
     * policyId lives in customer-service's own database - this is deliberately
     * NOT a JPA @ManyToOne/@JoinColumn, since a foreign key can't span two
     * separate microservice databases. Ownership/validity of the policy is
     * checked via CustomerClient (Feign) at claim-creation time, not enforced
     * by the database.
     */
    @Column(nullable = false)
    Long policyId;

    @Column(nullable = false)
    String incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ClaimStatus status;

    Long estimatedAmountCents;
    Long approvedAmountCents;

    @Column(nullable = false)
    Instant incidentDate;

    /**
     * Optimistic-lock version. Hibernate manages this: every committed UPDATE
     * increments it and filters the write with "AND version = <readVersion>",
     * so a stale concurrent writer affects 0 rows and surfaces as an
     * {@code OptimisticLockingFailureException} instead of silently overwriting
     * a newer status. Never set this from application code.
     */
    @Version
    Long version;

    @Column(nullable = false)
    @Builder.Default
    Instant createdAt = Instant.now();

    Instant updatedAt;

    Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ClaimStatus.SUBMITTED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
