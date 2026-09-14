package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByProviderEventId(String providerEventId);

    List<PaymentEvent> findByPurchaseId(Long purchaseId);
}
