package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {

    /**
     * Find all policy versions for a given policy, ordered by version number.
     * Includes plan relationship for complete version information.
     */
    @Query("""
        SELECT pv FROM PolicyVersion pv
        LEFT JOIN FETCH pv.plan
        WHERE pv.policy.id = :policyId
        ORDER BY pv.versionNumber ASC
        """)
    List<PolicyVersion> findByPolicyIdOrderByVersionNumber(@Param("policyId") Long policyId);
}
