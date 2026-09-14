package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByCustomerId(Long customerId);

    Optional<Purchase> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Purchase> findByIdempotencyKey(String idempotencyKey);
}
