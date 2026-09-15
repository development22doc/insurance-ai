package com.claimassist.platform.policy_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import com.claimassist.platform.policy_service.service.StripePaymentGateway;

@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseServiceCheckoutHelper {

    private final PurchaseRepository purchaseRepository;
    private final StripePaymentGateway stripePaymentGateway;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeCheckoutSession(Long purchaseId) {
        try {
            var purchaseOpt = purchaseRepository.findById(purchaseId);
            if (purchaseOpt.isEmpty()) {
                log.warn("Purchase not found when finalizing checkout session purchaseId={}", purchaseId);
                return;
            }
            var purchase = purchaseOpt.get();
            var checkout = stripePaymentGateway.prepareCheckoutSession(purchase);
            if (checkout.isPresent()) {
                var session = checkout.get();
                purchase.setProviderSessionId(session.sessionId());
                purchaseRepository.save(purchase);
                log.info("Finalized checkout session for purchaseId={} sessionId={}", purchaseId, session.sessionId());
            }
        } catch (Exception ex) {
            log.error("Failed to finalize checkout session for purchaseId={}", purchaseId, ex);
            // Do not throw - this runs after commit; failure to set session id is non-fatal; retries may be initiated by client.
        }
    }
}
