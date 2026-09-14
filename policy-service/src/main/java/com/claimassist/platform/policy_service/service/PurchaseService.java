package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PurchaseInitiationRequest;
import com.claimassist.platform.policy_service.dto.PurchaseResponse;
import com.claimassist.platform.policy_service.dto.PurchaseStatusResponse;
import com.claimassist.platform.policy_service.entity.CustomerPolicy;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.repository.CustomerPolicyRepository;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PurchaseService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final PurchaseRepository purchaseRepository;
    private final PlanRepository planRepository;
    private final CustomerPolicyRepository customerPolicyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StripePaymentGateway stripePaymentGateway;
    private final PolicyLifecycleService policyLifecycleService;

    @Transactional
    public PurchaseResponse initiatePurchase(PurchaseInitiationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        Long customerId = currentUserProvider.getCurrentUserId();
        Long planId = request.planId();

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));

        Product product = Optional.ofNullable(plan.getProduct())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "plan:" + planId));

        if (!ACTIVE_STATUS.equalsIgnoreCase(product.getStatus())) {
            throw new BadRequestException("Product is not available for purchase");
        }
        if (!ACTIVE_STATUS.equalsIgnoreCase(plan.getStatus())) {
            throw new BadRequestException("Plan is not available for purchase");
        }

        Long amountCents = plan.getAnnualPremiumCents();
        String currency = plan.getCurrency();
        if (amountCents == null || amountCents <= 0 || currency == null || currency.isBlank()) {
            throw new BadRequestException("Plan pricing information is unavailable");
        }

        Optional<Purchase> existing = purchaseRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Purchase existingPurchase = existing.get();
            if (!customerId.equals(existingPurchase.getCustomerId())) {
                throw new BadRequestException("Idempotency-Key already used by a different customer");
            }
            if (!planId.equals(existingPurchase.getPlan().getId())) {
                throw new BadRequestException("Idempotency-Key already used for a different plan");
            }
            return toResponse(existingPurchase);
        }

        Purchase purchase = Purchase.builder()
                .customerId(customerId)
                .plan(plan)
                .amountCents(amountCents)
                .currency(currency)
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey(idempotencyKey)
                .initiatedAt(Instant.now())
                .build();

        try {
            Purchase saved = purchaseRepository.saveAndFlush(purchase);
            StripeCheckoutSession checkoutSession = stripePaymentGateway.prepareCheckoutSession(saved).orElse(null);
            if (checkoutSession != null) {
                saved.setProviderSessionId(checkoutSession.sessionId());
                purchaseRepository.save(saved);
                log.info("Created pending purchase purchaseId={} customerId={} planId={} checkoutSessionId={}",
                        saved.getId(), customerId, planId, checkoutSession.sessionId());
                return createResponse(saved, checkoutSession.sessionId(), checkoutSession.checkoutUrl(), checkoutSession.testMode());
            }
            log.info("Created pending purchase purchaseId={} customerId={} planId={}", saved.getId(), customerId, planId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            Optional<Purchase> retryResult = purchaseRepository.findByIdempotencyKey(idempotencyKey);
            if (retryResult.isPresent()) {
                Purchase duplicate = retryResult.get();
                if (!customerId.equals(duplicate.getCustomerId())) {
                    throw new BadRequestException("Idempotency-Key already used by a different customer");
                }
                if (!planId.equals(duplicate.getPlan().getId())) {
                    throw new BadRequestException("Idempotency-Key already used for a different plan");
                }
                return toResponse(duplicate);
            }
            throw ex;
        }
    }

    public PurchaseStatusResponse getPurchaseStatus(Long purchaseId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        Purchase purchase = purchaseRepository.findByIdAndCustomerId(purchaseId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", String.valueOf(purchaseId)));
        return toStatusResponse(purchase);
    }

    @Transactional
    public void applyWebhookState(Long purchaseId, String stripeEventType) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", String.valueOf(purchaseId)));

        PurchaseStatus targetStatus = resolveTargetStatus(purchase.getStatus(), stripeEventType);
        if (targetStatus == null) {
            log.debug("Ignoring Stripe event={} for purchaseId={} in status={}", stripeEventType, purchaseId, purchase.getStatus());
            return;
        }

        applyStateTransition(purchase, targetStatus);
        purchaseRepository.save(purchase);
    }

    private PurchaseStatus resolveTargetStatus(PurchaseStatus currentStatus, String stripeEventType) {
        if (stripeEventType == null) {
            return null;
        }

        return switch (stripeEventType) {
            case "checkout.session.completed", "payment_intent.processing", "checkout.session.async_payment_started" ->
                    transitionTo(currentStatus, PurchaseStatus.PAYMENT_PROCESSING) ? PurchaseStatus.PAYMENT_PROCESSING : null;
            case "payment_intent.succeeded", "checkout.session.async_payment_succeeded", "charge.succeeded" ->
                    transitionTo(currentStatus, PurchaseStatus.PAID) ? PurchaseStatus.PAID : null;
            case "payment_intent.payment_failed", "checkout.session.async_payment_failed", "charge.failed" ->
                    transitionTo(currentStatus, PurchaseStatus.PAYMENT_FAILED) ? PurchaseStatus.PAYMENT_FAILED : null;
            case "checkout.session.expired" ->
                    transitionTo(currentStatus, PurchaseStatus.EXPIRED) ? PurchaseStatus.EXPIRED : null;
            case "checkout.session.cancelled" ->
                    transitionTo(currentStatus, PurchaseStatus.CANCELLED) ? PurchaseStatus.CANCELLED : null;
            default -> null;
        };
    }

    private boolean transitionTo(PurchaseStatus currentStatus, PurchaseStatus targetStatus) {
        if (currentStatus == null) {
            return false;
        }

        Set<PurchaseStatus> validTransitions = switch (currentStatus) {
            case PENDING_PAYMENT -> EnumSet.of(PurchaseStatus.PAYMENT_PROCESSING, PurchaseStatus.PAYMENT_FAILED, PurchaseStatus.CANCELLED, PurchaseStatus.EXPIRED);
            case PAYMENT_PROCESSING -> EnumSet.of(PurchaseStatus.PAID, PurchaseStatus.PAYMENT_FAILED);
            case PAYMENT_FAILED, PAID, CANCELLED, EXPIRED -> EnumSet.noneOf(PurchaseStatus.class);
        };

        return validTransitions.contains(targetStatus)
                || (currentStatus == PurchaseStatus.PENDING_PAYMENT && targetStatus == PurchaseStatus.PAYMENT_PROCESSING);
    }

    private void applyStateTransition(Purchase purchase, PurchaseStatus nextStatus) {
        PurchaseStatus previousStatus = purchase.getStatus();
        if (!transitionTo(previousStatus, nextStatus)) {
            throw new BadRequestException("Invalid purchase state transition from " + previousStatus + " to " + nextStatus);
        }

        purchase.setStatus(nextStatus);
        Instant now = Instant.now();

        switch (nextStatus) {
            case PAYMENT_PROCESSING -> purchase.setStatus(PurchaseStatus.PAYMENT_PROCESSING);
            case PAYMENT_FAILED -> {
                purchase.setFailedAt(now);
                purchase.setStatus(PurchaseStatus.PAYMENT_FAILED);
            }
            case PAID -> {
                purchase.setPaidAt(now);
                purchase.setStatus(PurchaseStatus.PAID);
                policyLifecycleService.createInitialPolicyForPurchase(purchase, now);
            }
            case CANCELLED -> {
                purchase.setCancelledAt(now);
                purchase.setStatus(PurchaseStatus.CANCELLED);
            }
            case EXPIRED -> purchase.setStatus(PurchaseStatus.EXPIRED);
            case PENDING_PAYMENT -> {
                // no-op; purchase creation already starts in this state and should not be re-entered by provider events.
            }
        }

        log.info("Purchase state transition purchaseId={} {} -> {}", purchase.getId(), previousStatus, nextStatus);
    }

    private void activateCustomerPolicy(Purchase purchase) {
        if (purchase.getCustomerPolicy() != null) {
            return;
        }

        String policyNumber = "POL-" + purchase.getCustomerId() + "-" + purchase.getId();
        CustomerPolicy customerPolicy = customerPolicyRepository.findByCustomerIdAndPlanIdAndStatus(
                        purchase.getCustomerId(), purchase.getPlan().getId(), "ACTIVE")
                .orElse(null);

        if (customerPolicy == null) {
            customerPolicy = CustomerPolicy.builder()
                    .customerId(purchase.getCustomerId())
                    .plan(purchase.getPlan())
                    .policyNumber(policyNumber)
                    .status("ACTIVE")
                    .effectiveDate(Instant.now())
                    .activatedAt(Instant.now())
                    .build();
            customerPolicyRepository.save(customerPolicy);
        }

        purchase.setCustomerPolicy(customerPolicy);
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        Product product = purchase.getPlan().getProduct();
        String checkoutSessionId = purchase.getProviderSessionId();
        String checkoutUrl = null;
        Boolean stripeTestMode = false;

        if (checkoutSessionId == null && stripePaymentGateway.isEnabled()) {
            Optional<StripeCheckoutSession> checkoutSession = stripePaymentGateway.prepareCheckoutSession(purchase);
            if (checkoutSession.isPresent()) {
                StripeCheckoutSession session = checkoutSession.get();
                checkoutSessionId = session.sessionId();
                checkoutUrl = session.checkoutUrl();
                stripeTestMode = session.testMode();
            }
        }

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getCustomerId(),
                purchase.getPlan().getId(),
                product.getId(),
                purchase.getPlan().getName(),
                product.getName(),
                purchase.getStatus().name(),
                purchase.getAmountCents(),
                purchase.getCurrency(),
                purchase.getIdempotencyKey(),
                purchase.getInitiatedAt(),
                purchase.getCreatedAt(),
                checkoutSessionId,
                checkoutUrl,
                stripeTestMode
        );
    }

    private PurchaseResponse createResponse(Purchase purchase, String checkoutSessionId, String checkoutUrl, Boolean stripeTestMode) {
        Product product = purchase.getPlan().getProduct();
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getCustomerId(),
                purchase.getPlan().getId(),
                product.getId(),
                purchase.getPlan().getName(),
                product.getName(),
                purchase.getStatus().name(),
                purchase.getAmountCents(),
                purchase.getCurrency(),
                purchase.getIdempotencyKey(),
                purchase.getInitiatedAt(),
                purchase.getCreatedAt(),
                checkoutSessionId,
                checkoutUrl,
                stripeTestMode != null && stripeTestMode
        );
    }

    private PurchaseStatusResponse toStatusResponse(Purchase purchase) {
        Product product = purchase.getPlan().getProduct();
        return new PurchaseStatusResponse(
                purchase.getId(),
                purchase.getCustomerId(),
                purchase.getPlan().getId(),
                product.getId(),
                purchase.getStatus().name(),
                purchase.getAmountCents(),
                purchase.getCurrency(),
                purchase.getInitiatedAt(),
                purchase.getCreatedAt()
        );
    }
}
