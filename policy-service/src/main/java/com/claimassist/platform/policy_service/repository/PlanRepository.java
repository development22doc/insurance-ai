package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Plan;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends CrudRepository<Plan, Long> {

    Optional<Plan> findByCodeAndProductId(String code, Long productId);
}