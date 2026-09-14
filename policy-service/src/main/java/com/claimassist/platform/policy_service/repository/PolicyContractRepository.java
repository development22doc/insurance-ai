package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.PolicyContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyContractRepository extends JpaRepository<PolicyContract, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PolicyContract> findByCustomerIdAndPolicyNumber(Long customerId, String policyNumber);

    @Query(value = "SELECT * FROM policy_contracts WHERE customer_id = :customerId AND policy_number = :policyNumber FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<PolicyContract> findForUpdateSkipLocked(@Param("customerId") Long customerId, @Param("policyNumber") String policyNumber);

    @EntityGraph(attributePaths = {"currentPolicyPeriod"})
    List<PolicyContract> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"currentPolicyPeriod"})
    Optional<PolicyContract> findByIdAndCustomerId(Long id, Long customerId);
}
