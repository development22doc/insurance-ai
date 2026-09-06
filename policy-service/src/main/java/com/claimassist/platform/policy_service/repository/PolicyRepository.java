package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.dto.PolicyCoverageProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyRepository extends CrudRepository<com.claimassist.platform.policy_service.entity.Policy, Long> {

    java.util.Optional<com.claimassist.platform.policy_service.entity.Policy> findByPolicyNumber(String policyNumber);

    @Query(value = "SELECT p.id AS policyId, p.policy_number AS policyNumber, p.status AS status, \n" +
            "       pr.code AS productType, pl.name AS coveragePlanName, \n" +
            "       COALESCE(pv.deductible_cents, pl.deductible_cents) AS deductibleCents, \n" +
            "       COALESCE(pv.coverage_limit_cents, pl.coverage_limit_cents) AS coverageLimitCents, \n" +
            "       CASE WHEN p.renewal_date IS NULL THEN NULL ELSE to_char(p.renewal_date, 'YYYY-MM-DD') END AS renewalDate \n" +
            "FROM policies p \n" +
            "JOIN policy_version pv ON pv.policy_id = p.id \n" +
            "JOIN plans pl ON pv.plan_id = pl.id \n" +
            "JOIN products pr ON pl.product_id = pr.id \n" +
            "WHERE p.id = :policyId \n" +
            "  AND p.customer_id = :customerId \n" +
            "  AND pv.version_number = (SELECT MAX(version_number) FROM policy_version WHERE policy_id = p.id)",
            nativeQuery = true)
    PolicyCoverageProjection findPolicyCoverageProjectionByPolicyIdAndCustomerId(@Param("policyId") Long policyId,
                                                                                   @Param("customerId") Long customerId);
}

