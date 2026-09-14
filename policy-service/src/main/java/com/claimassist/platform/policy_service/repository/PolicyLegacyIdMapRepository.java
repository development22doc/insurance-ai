package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyLegacyIdMapRepository extends JpaRepository<PolicyLegacyIdMap, Long> {

    boolean existsByLegacySourceAndLegacyPolicyId(String legacySource, Long legacyPolicyId);

    Optional<PolicyLegacyIdMap> findByLegacySourceAndLegacyPolicyId(String legacySource, Long legacyPolicyId);
}
