package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.CustomerPolicy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerPolicyRepository extends JpaRepository<CustomerPolicy, Long> {

    @EntityGraph(attributePaths = {"plan", "plan.product"})
    List<CustomerPolicy> findByCustomerIdOrderByEffectiveDateDesc(Long customerId);

    @EntityGraph(attributePaths = {"plan", "plan.product"})
    Optional<CustomerPolicy> findByIdAndCustomerId(Long id, Long customerId);

    Optional<CustomerPolicy> findByCustomerIdAndPlanIdAndStatus(Long customerId, Long planId, String status);
}
