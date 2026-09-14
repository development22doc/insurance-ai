package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PolicyPeriodRepository extends JpaRepository<PolicyPeriod, Long> {

    @EntityGraph(attributePaths = {"policyContract", "previousPolicyPeriod"})
    List<PolicyPeriod> findByPolicyContractIdOrderByRenewalSequenceAsc(Long policyContractId);

    @EntityGraph(attributePaths = {"policyContract", "previousPolicyPeriod"})
    List<PolicyPeriod> findByPolicyContractIdOrderByEffectiveDateAsc(Long policyContractId);

    @Query("""
            select pp from PolicyPeriod pp
            where pp.policyContract.id = :policyContractId
              and pp.effectiveDate <= :incidentTime
              and pp.expirationDate > :incidentTime
            order by pp.effectiveDate desc
            """)
    List<PolicyPeriod> findCoveringPeriodsForContractAt(
            @Param("policyContractId") Long policyContractId,
            @Param("incidentTime") Instant incidentTime);
}
