package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PurchaseInitiationRequest;
import com.claimassist.platform.policy_service.dto.PurchaseResponse;
import com.claimassist.platform.policy_service.dto.PurchaseStatusResponse;
import com.claimassist.platform.policy_service.entity.CustomerPolicy;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.repository.CustomerPolicyRepository;
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
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private CustomerPolicyRepository customerPolicyRepository;

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
    void initiatePurchase_createsPendingPurchaseAndCapturesAuthoritativePriceSnapshot() {
        when(stripePaymentGateway.prepareCheckoutSession(any())).thenReturn(Optional.empty());
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

        PurchaseResponse response = purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "key-1");

        assertThat(response.customerId()).isEqualTo(42L);
        assertThat(response.planId()).isEqualTo(22L);
        assertThat(response.productId()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.amountCents()).isEqualTo(50000L);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void initiatePurchase_preparesStripeCheckoutSessionWhenEnabled() {
        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.findByIdempotencyKey("key-checkout")).thenReturn(Optional.empty());
        when(stripePaymentGateway.prepareCheckoutSession(any())).thenReturn(Optional.of(
                new StripeCheckoutSession("cs_test_123", "https://checkout.example/test", true)));

        Purchase saved = Purchase.builder()
                .id(91L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("key-checkout")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        when(purchaseRepository.saveAndFlush(any(Purchase.class))).thenReturn(saved);

        PurchaseResponse response = purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "key-checkout");

        assertThat(response.checkoutSessionId()).isEqualTo("cs_test_123");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.example/test");
        assertThat(response.stripeTestMode()).isTrue();
    }

    void initiatePurchase_returnsExistingPurchaseForSameCustomerAndIdempotencyKey() {
        Purchase existing = Purchase.builder()
                .id(88L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("key-dup")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existing));

        PurchaseResponse response = purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "key-dup");

        assertThat(response.purchaseId()).isEqualTo(88L);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void initiatePurchase_rejectsDifferentCustomerUsingSameIdempotencyKey() {
        Purchase existing = Purchase.builder()
                .id(89L)
                .customerId(999L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("key-dup")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "key-dup"));
    }

    @Test
    void initiatePurchase_rejectsInactivePlan() {
        plan.setStatus("INACTIVE");
        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));

        assertThrows(BadRequestException.class,
                () -> purchaseService.initiatePurchase(new PurchaseInitiationRequest(22L), "key-inactive"));
    }

    @Test
    void initiatePurchase_throwsWhenPlanIsMissing() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> purchaseService.initiatePurchase(new PurchaseInitiationRequest(999L), "key-missing"));
    }

    @Test
    void getPurchaseStatus_returnsOwnedPurchaseStatus() {
        Purchase purchase = Purchase.builder()
                .id(50L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("status-key")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(purchaseRepository.findByIdAndCustomerId(50L, 42L)).thenReturn(Optional.of(purchase));

        PurchaseStatusResponse response = purchaseService.getPurchaseStatus(50L);

        assertThat(response.purchaseId()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.customerId()).isEqualTo(42L);
    }

    @Test
    void applyWebhookState_movesPendingPurchaseToPaymentProcessing() {
        Purchase purchase = Purchase.builder()
                .id(150L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey("processing-key")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(purchaseRepository.findById(150L)).thenReturn(Optional.of(purchase));

        purchaseService.applyWebhookState(150L, "checkout.session.completed");

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PAYMENT_PROCESSING);
    }

    @Test
    void applyWebhookState_marksPurchasePaidAndCreatesPolicyContract() {
        Purchase purchase = Purchase.builder()
                .id(151L)
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PAYMENT_PROCESSING)
                .idempotencyKey("paid-key")
                .initiatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(purchaseRepository.findById(151L)).thenReturn(Optional.of(purchase));
        when(policyLifecycleService.createInitialPolicyForPurchase(any(Purchase.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Purchase target = invocation.getArgument(0);
                    Instant effectiveDate = invocation.getArgument(1);
                    PolicyContract contract = PolicyContract.builder()
                            .id(2001L)
                            .customerId(target.getCustomerId())
                            .productId(target.getPlan().getProduct().getId())
                            .policyNumber("POL-42-151")
                            .status("ACTIVE")
                            .build();
                    PolicyPeriod period = PolicyPeriod.builder()
                            .id(3001L)
                            .policyContract(contract)
                            .planId(target.getPlan().getId())
                            .renewalSequence(0)
                            .status("ACTIVE")
                            .effectiveDate(effectiveDate)
                            .expirationDate(effectiveDate.plusSeconds(31536000L))
                            .renewalDate(effectiveDate.plusSeconds(31536000L))
                            .activatedAt(effectiveDate)
                            .build();
                    contract.setCurrentPolicyPeriod(period);
                    target.setPolicyContract(contract);
                    target.setTargetPolicyPeriod(period);
                    return contract;
                });

        purchaseService.applyWebhookState(151L, "payment_intent.succeeded");

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(purchase.getPolicyContract()).isNotNull();
        assertThat(purchase.getPolicyContract().getPolicyNumber()).isEqualTo("POL-42-151");
        assertThat(purchase.getPolicyContract().getCurrentPolicyPeriod()).isNotNull();
        assertThat(purchase.getPolicyContract().getCurrentPolicyPeriod().getPlanId()).isEqualTo(22L);
    }

    @Test
    void getPurchaseStatus_rejectsAnotherCustomersPurchase() {
        when(purchaseRepository.findByIdAndCustomerId(60L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> purchaseService.getPurchaseStatus(60L));
    }
}
