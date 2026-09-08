package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.dto.PolicyCoverageProjection;
import com.claimassist.platform.policy_service.entity.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends CrudRepository<Policy, Long> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    @EntityGraph(attributePaths = {"coveragePlan", "coveragePlan.product"})
    Optional<Policy> findByIdAndCustomerId(Long policyId, Long customerId);

    /**
     * Find policy and acquire a pessimistic write lock for concurrency-sensitive operations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"coveragePlan", "coveragePlan.product"})
    @Query("select p from Policy p where p.id = :id")
    Optional<Policy> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"coveragePlan", "coveragePlan.product"})
    List<Policy> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /**
     * Get all policies with pagination for admin/operations use.
     * Uses EntityGraph to efficiently load plan and product relationships.
     */
    @EntityGraph(attributePaths = {"coveragePlan", "coveragePlan.product"})
    Page<Policy> findAll(Pageable pageable);

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
            "  AND pv.version_number = (SELECT MAX(version_number) FROM policy_version WHERE policy_id = p.id)",
            nativeQuery = true)
    PolicyCoverageProjection findPolicyCoverageProjectionByPolicyId(@Param("policyId") Long policyId);

    @Query(value = "SELECT p.id AS policyId, p.policy_number AS policyNumber, p.status AS status, \n" +
            "       pr.code AS productType, pl.name AS coveragePlanName, \n" +
            "       COALESCE(pv.deductible_cents, pl.deductible_cents) AS deductibleCents, \n" +
            "       COALESCE(pv.coverage_limit_cents, pl.coverage_limit_cents) AS coverageLimitCents, \n" +
            "       CASE WHEN p.renewal_date IS NULL THEN NULL ELSE to_char(p.renewal_date, 'YYYY-MM-DD') END AS renewalDate \n" +
            "FROM policies p \n" +
            "JOIN policy_version pv ON pv.policy_id = p.id \n" +
            "JOIN plans pl ON pv.plan_id = pl.id \n" +
            "JOIN products pr ON pl.product_id = pr.id \n" +
            "WHERE p.customer_id = :customerId \n" +
            "  AND pv.version_number = (SELECT MAX(version_number) FROM policy_version WHERE policy_id = p.id)\n" +
            "ORDER BY p.created_at DESC",
            nativeQuery = true)
    java.util.List<PolicyCoverageProjection> findPolicyCoverageProjectionByCustomerId(@Param("customerId") Long customerId);
}

