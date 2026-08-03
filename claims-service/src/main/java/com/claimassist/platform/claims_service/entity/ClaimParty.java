package com.claimassist.platform.claims_service.entity;

import com.claimassist.platform.common_lib.enums.ClaimRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Who is attached to this claim, and in what capacity - POLICYHOLDER (the
 * claimant), ADJUSTER (handles the claim), or AUDITOR (read-only, full
 * visibility including rejected AI proposals). Composite key on
 * (claimId, userId) - same technique as ProjectMember in the Lovable clone.
 */
@Entity
@Table(name = "claim_parties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClaimParty {

    @EmbeddedId
    ClaimPartyId id;

    @ManyToOne
    @MapsId("claimId")
    @JoinColumn(name = "claim_id")
    Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ClaimRole claimRole;

    @Builder.Default
    Instant addedAt = Instant.now();
}
