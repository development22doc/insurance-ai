package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PurchaseInitiationRequest;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceIdempotencyTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StripePaymentGateway stripePaymentGateway;

    @Mock
    private PolicyLifecycleService policyLifecycleService;

    @InjectMocks
    private PurchaseService purchaseService;

    private Product product;
    private Plan plan;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        product = Product.builder()
                .id(11L)
                .name("Auto Insurance")
                .status("ACTIVE")
                .build();

        plan = Plan.builder()
                .id(22L)
                .product(product)
                .name("Comprehensive Auto")
                .status("ACTIVE")
                .annualPremiumCents(50000L)
                .currency("INR")
                .build();
    }

    @Test
    void initiatePurchase_trimsIdempotencyKey() {
        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        Purchase saved = Purchase.builder()
                .id(77L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("key-1")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        when(purchaseRepository.saveAndFlush(any(Purchase.class))).thenReturn(saved);

        var response = purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "  key-1  ");
        assertThat(response.customerId()).isEqualTo(42L);
    }

    @Test
    void initiatePurchase_rejectsTooLongIdempotencyKey() {
        String longKey = "k".repeat(300);
        assertThrows(BadRequestException.class,
                () -> purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), longKey));
    }
}
