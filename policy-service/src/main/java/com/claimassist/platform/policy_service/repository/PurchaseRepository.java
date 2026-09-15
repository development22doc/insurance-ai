package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.entity.PurchaseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByCustomerId(Long customerId);

    Optional<Purchase> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Purchase> findByIdempotencyKey(String idempotencyKey);

    Optional<Purchase> findBySourcePolicyPeriodIdAndPurchaseTypeAndStatusIn(
            Long sourcePolicyPeriodId,
            PurchaseType purchaseType,
            Set<PurchaseStatus> statuses);
}
