package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PurchaseServiceWebhookOptimisticLockTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private StripePaymentGateway stripePaymentGateway;

    @Mock
    private PolicyLifecycleService policyLifecycleService;

    @InjectMocks
    private PurchaseService purchaseService;

    private Purchase purchase;

    @BeforeEach
    void setUp() {
        purchase = Purchase.builder()
                .id(200L)
                .customerId(42L)
                .amountCents(100L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .initiatedAt(Instant.now())
                .build();

        // allow lenient stub for initial findById
        org.mockito.Mockito.lenient().when(purchaseRepository.findById(200L)).thenReturn(Optional.of(purchase));
    }

    @Test
    void optimisticLockReloadShowsAlreadyAdvanced_thenAcceptsEvent() {
        // Simulate save throwing optimistic locking exception
        org.mockito.Mockito.doThrow(new ObjectOptimisticLockingFailureException(Purchase.class, 200L))
                .when(purchaseRepository).save(org.mockito.ArgumentMatchers.any(Purchase.class));
        // After reload, purchase now shows PAID (i.e., another tx applied the PAID transition)
        Purchase reloaded = Purchase.builder().id(200L).status(PurchaseStatus.PAID).build();
        when(purchaseRepository.findById(200L)).thenReturn(Optional.of(purchase), Optional.of(reloaded));

        // Should not throw; considered already applied/obsolete
        assertDoesNotThrow(() -> purchaseService.applyWebhookState(200L, "payment_intent.succeeded"));
    }

    @Test
    void optimisticLockReloadShowsStillPending_thenRethrowToAllowRetry() {
        org.mockito.Mockito.doThrow(new ObjectOptimisticLockingFailureException(Purchase.class, 200L))
                .when(purchaseRepository).save(org.mockito.ArgumentMatchers.any(Purchase.class));
        // After reload, still pending -> event still matters
        Purchase reloaded = Purchase.builder().id(200L).status(PurchaseStatus.PENDING_PAYMENT).build();
        when(purchaseRepository.findById(200L)).thenReturn(Optional.of(purchase), Optional.of(reloaded));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> purchaseService.applyWebhookState(200L, "checkout.session.completed"));
    }
}
