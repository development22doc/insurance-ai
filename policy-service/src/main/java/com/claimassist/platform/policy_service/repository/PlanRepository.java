package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCode(String code);

    List<Plan> findByProductId(Long productId);

    List<Plan> findByStatus(String status);

    List<Plan> findByProductIdAndStatus(Long productId, String status);

    Optional<Plan> findByIdAndStatus(Long planId, String status);
}
