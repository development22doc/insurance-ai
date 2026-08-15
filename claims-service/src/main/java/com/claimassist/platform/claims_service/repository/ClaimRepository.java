package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /**
     * "My claims" listing. Uses a constructor projection ({@link ClaimSummaryRow}) that selects
     * only the columns needed to build {@code ClaimSummaryResponse}, rather than hydrating full
     * {@link Claim} entities (version/updatedAt/deletedAt are not needed for the summary). This is
     * a pure read-only reduction in data loaded and entity materialization; the WHERE/JOIN/ORDER BY
     * (and thus the {@code idx_claim_parties_user_id} usage) are unchanged.
     */
    @Query("""
        SELECT new com.claimassist.platform.claims_service.repository.ClaimSummaryRow(
            c.id, c.claimNumber, c.policyId, c.incidentType, c.status,
            c.estimatedAmountCents, c.approvedAmountCents, c.incidentDate, c.createdAt, cp.claimRole)
        FROM Claim c
        JOIN ClaimParty cp ON cp.claim.id = c.id
        WHERE cp.id.userId = :userId AND c.deletedAt IS NULL
        ORDER BY c.createdAt DESC
        """)
    List<ClaimSummaryRow> findAllAccessibleByUser(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM Claim c
        JOIN ClaimParty cp ON cp.claim.id = c.id
        WHERE c.id = :claimId AND cp.id.userId = :userId AND c.deletedAt IS NULL
        """)
    Optional<Claim> findAccessibleClaimById(@Param("claimId") Long claimId, @Param("userId") Long userId);

    Optional<Claim> findByClaimNumber(String claimNumber);

    interface ClaimWithRoleProjection {
        Claim getClaim();
        ClaimRole getRole();
    }
}
