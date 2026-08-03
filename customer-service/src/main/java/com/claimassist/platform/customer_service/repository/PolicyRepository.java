package com.claimassist.platform.customer_service.repository;

import com.claimassist.platform.customer_service.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    @Query("SELECT p FROM Policy p WHERE p.id = :policyId AND p.customer.id = :customerId")
    Optional<Policy> findByIdAndCustomerId(@Param("policyId") Long policyId, @Param("customerId") Long customerId);

    List<Policy> findByCustomerId(Long customerId);
}
