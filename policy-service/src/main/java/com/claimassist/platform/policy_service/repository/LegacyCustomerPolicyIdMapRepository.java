package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.LegacyCustomerPolicyIdMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LegacyCustomerPolicyIdMapRepository extends JpaRepository<LegacyCustomerPolicyIdMap, Long> {

    Optional<LegacyCustomerPolicyIdMap> findByLegacyCustomerPolicyId(Long legacyCustomerPolicyId);

    Optional<LegacyCustomerPolicyIdMap> findByPolicyServicePolicyId(Long policyServicePolicyId);

    Optional<LegacyCustomerPolicyIdMap> findByLegacyPolicyNumber(String legacyPolicyNumber);

    @Query("""
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
        FROM LegacyCustomerPolicyIdMap l
        WHERE l.legacyCustomerPolicyId = :legacyPolicyId
        """)
    boolean existsByLegacyCustomerPolicyId(@Param("legacyPolicyId") Long legacyPolicyId);

    @Query("""
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
        FROM LegacyCustomerPolicyIdMap l
        WHERE l.policyServicePolicyId = :policyId
        """)
    boolean existsByPolicyServicePolicyId(@Param("policyId") Long policyId);
}
