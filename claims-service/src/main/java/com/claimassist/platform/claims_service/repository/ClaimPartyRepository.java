package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.entity.ClaimPartyId;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimPartyRepository extends JpaRepository<ClaimParty, ClaimPartyId> {

    @Query("SELECT cp.claimRole FROM ClaimParty cp WHERE cp.id.claimId = :claimId AND cp.id.userId = :userId")
    Optional<ClaimRole> findRoleByClaimIdAndUserId(@Param("claimId") Long claimId, @Param("userId") Long userId);

    List<ClaimParty> findByIdClaimId(Long claimId);
}
