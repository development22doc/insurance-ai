package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "policy_legacy_id_map",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_policy_legacy_id_map_source_policy_id", columnNames = {"legacy_source", "legacy_policy_id"})
        },
        indexes = {
                @Index(name = "idx_policy_legacy_id_map_policy_contract_id", columnList = "policy_contract_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolicyLegacyIdMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    @Column(nullable = false)
    Long version;

    @Column(name = "legacy_source", nullable = false, length = 64)
    String legacySource;

    @Column(name = "legacy_policy_id", nullable = false)
    Long legacyPolicyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_contract_id", nullable = false)
    PolicyContract policyContract;

    @Column(name = "legacy_record_type", nullable = false, length = 64)
    String legacyRecordType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
