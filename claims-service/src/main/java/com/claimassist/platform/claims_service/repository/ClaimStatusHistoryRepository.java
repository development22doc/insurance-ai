package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
    List<ClaimStatusHistory> findByClaimIdOrderByChangedAtAsc(Long claimId);
}
