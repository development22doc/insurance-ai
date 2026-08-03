package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Query("""
        SELECT c AS claim, cp.claimRole AS role
        FROM Claim c
        JOIN ClaimParty cp ON cp.claim.id = c.id
        WHERE cp.id.userId = :userId AND c.deletedAt IS NULL
        ORDER BY c.createdAt DESC
        """)
    List<ClaimWithRoleProjection> findAllAccessibleByUser(@Param("userId") Long userId);

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
