package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
