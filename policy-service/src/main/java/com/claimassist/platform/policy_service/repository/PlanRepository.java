package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Plan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends CrudRepository<Plan, Long> {

    Optional<Plan> findByCodeAndProductId(String code, Long productId);

    Optional<Plan> findByCodeIgnoreCaseAndProductId(String code, Long productId);

    /**
     * Find all plans for a product with product relationship loaded.
     */
    @EntityGraph(attributePaths = {"product"})
    List<Plan> findByProductIdOrderByCreatedAtDesc(Long productId);

    /**
     * Find a plan by ID with product relationship loaded.
     */
    @EntityGraph(attributePaths = {"product"})
    Optional<Plan> findById(Long id);
}
