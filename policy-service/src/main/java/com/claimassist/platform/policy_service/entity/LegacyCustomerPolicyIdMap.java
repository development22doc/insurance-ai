package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "legacy_customer_policy_id_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LegacyCustomerPolicyIdMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "legacy_customer_policy_id", nullable = false, unique = true)
    Long legacyCustomerPolicyId;

    @Column(name = "policy_service_policy_id", nullable = false, unique = true)
    Long policyServicePolicyId;

    @Column(name = "source_system", nullable = false)
    String sourceSystem;

    @Column(name = "legacy_customer_id")
    Long legacyCustomerId;

    @Column(name = "legacy_policy_number")
    String legacyPolicyNumber;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (sourceSystem == null) sourceSystem = "customer_service";
    }
}
