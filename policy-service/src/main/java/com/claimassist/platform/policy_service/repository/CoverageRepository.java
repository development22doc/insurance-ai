package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Coverage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoverageRepository extends CrudRepository<Coverage, Long> {

    Optional<Coverage> findByCodeAndPlanId(String code, Long planId);
}