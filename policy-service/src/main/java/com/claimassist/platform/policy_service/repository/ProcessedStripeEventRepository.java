package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
