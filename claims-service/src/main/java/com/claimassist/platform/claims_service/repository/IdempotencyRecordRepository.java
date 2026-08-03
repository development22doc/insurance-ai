package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
