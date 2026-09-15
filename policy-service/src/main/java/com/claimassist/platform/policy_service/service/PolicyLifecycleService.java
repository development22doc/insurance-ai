package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.RenewalInitiationResponse;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.entity.PurchaseType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyLifecycleService {

    private static final String ACTIVE_POLICY_STATUS = "ACTIVE";
    private static final String CANCELLED_POLICY_STATUS = "CANCELLED";
    private static final long DEFAULT_POLICY_DURATION_DAYS = 365L;

    private final PolicyContractRepository policyContractRepository;
    private final PolicyPeriodRepository policyPeriodRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlanRepository planRepository;
    private final StripePaymentGateway stripePaymentGateway;
    private final CacheManager cacheManager;

    @Transactional
    public PolicyContract createInitialPolicyForPurchase(Purchase purchase, Instant paymentConfirmedAt) {
        if (purchase == null) {
            throw new BadRequestException("Purchase is required to create a policy");
        }
        if (purchase.getPolicyContract() != null) {
            return purchase.getPolicyContract();
        }

        Long customerId = purchase.getCustomerId();
        if (customerId == null) {
            throw new BadRequestException("Purchase is missing customer identity");
        }

        Plan plan = purchase.getPlan();
        if (plan == null || plan.getId() == null) {
            throw new ResourceNotFoundException("Plan", "purchase:" + purchase.getId());
        }

        String policyNumber = generatePolicyNumber(customerId, purchase.getId());
        PolicyContract existing = policyContractRepository.findByCustomerIdAndPolicyNumber(customerId, policyNumber)
                .orElse(null);
        if (existing != null) {
            purchase.setPolicyContract(existing);
            return existing;
        }

        Instant effectiveDate = paymentConfirmedAt != null ? paymentConfirmedAt : Instant.now();
        PolicyContract contract = PolicyContract.builder()
                .customerId(customerId)
                .productId(plan.getProduct() != null ? plan.getProduct().getId() : null)
                .policyNumber(policyNumber)
                .status(ACTIVE_POLICY_STATUS)
                .build();
        try {
            contract = policyContractRepository.saveAndFlush(contract);
        } catch (DataIntegrityViolationException ex) {
            // Another concurrent activation likely created the contract — reload and verify the activation invariant.
            PolicyContract existingContract = policyContractRepository.findByCustomerIdAndPolicyNumber(customerId, policyNumber)
                    .orElseThrow(() -> ex);
            // Verify the existing contract has the expected current policy period; if not, treat as incomplete and fail so caller may retry.
            if (existingContract.getCurrentPolicyPeriod() != null) {
                purchase.setPolicyContract(existingContract);
                return existingContract;
            }
            // Invariant incomplete: rethrow to surface the failure so it can be retried safely by the caller / webhook retry mechanism.
            throw ex;
        }

        PolicyPeriod period = PolicyPeriod.builder()
                .policyContract(contract)
                .previousPolicyPeriod(null)
                .planId(plan.getId())
                .renewalSequence(0)
                .status(ACTIVE_POLICY_STATUS)
                .effectiveDate(effectiveDate)
                .expirationDate(effectiveDate.plus(DEFAULT_POLICY_DURATION_DAYS, ChronoUnit.DAYS))
                .renewalDate(effectiveDate.plus(DEFAULT_POLICY_DURATION_DAYS, ChronoUnit.DAYS))
                .activatedAt(effectiveDate)
                .build();
        try {
            period = policyPeriodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException ex) {
            // If period insertion collides, reload the contract and verify that it has a complete current policy period.
            PolicyContract reloaded = policyContractRepository.findById(contract.getId()).orElse(contract);
            if (reloaded.getCurrentPolicyPeriod() != null
                    && reloaded.getCurrentPolicyPeriod().getPlanId() != null
                    && reloaded.getCurrentPolicyPeriod().getPlanId().equals(plan.getId())
                    && reloaded.getCurrentPolicyPeriod().getRenewalSequence() != null
                    && reloaded.getCurrentPolicyPeriod().getRenewalSequence() == 0) {
                purchase.setPolicyContract(reloaded);
                return reloaded;
            }
            // Incomplete activation — surface the exception so the caller/webhook may retry.
            throw ex;
        }

        contract.setCurrentPolicyPeriod(period);
        contract = policyContractRepository.save(contract);
        purchase.setPolicyContract(contract);
        purchase.setTargetPolicyPeriod(period);
        purchase.setSourcePolicyPeriod(null);

        invalidateCustomerPolicyCaches(customerId, contract.getId());

        log.info("Created PolicyContract policyId={} policyNumber={} purchaseId={} planId={} policyPeriodId={} effectiveDate={} expirationDate={}",
                contract.getId(), contract.getPolicyNumber(), purchase.getId(), plan.getId(), period.getId(), period.getEffectiveDate(), period.getExpirationDate());
        return contract;
    }

    @Transactional
    public PolicyContract cancelPolicy(Long policyId, Long customerId, Instant cancelledAt) {
        if (policyId == null) {
            throw new BadRequestException("Policy id is required for cancellation");
        }
        if (customerId == null) {
            throw new BadRequestException("Customer id is required for cancellation");
        }

        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        if (CANCELLED_POLICY_STATUS.equalsIgnoreCase(contract.getStatus())) {
            log.info("Policy already cancelled policyId={} customerId={}", policyId, customerId);
            return contract;
        }

        if (!ACTIVE_POLICY_STATUS.equalsIgnoreCase(contract.getStatus())) {
            throw new BadRequestException("Cannot cancel policy in status: " + contract.getStatus());
        }

        Instant cancellationTime = cancelledAt != null ? cancelledAt : Instant.now();
        contract.setStatus(CANCELLED_POLICY_STATUS);

        PolicyPeriod currentPeriod = contract.getCurrentPolicyPeriod();
        if (currentPeriod != null) {
            currentPeriod.setStatus(CANCELLED_POLICY_STATUS);
            currentPeriod.setCancelledAt(cancellationTime);
            policyPeriodRepository.save(currentPeriod);
        }

        contract = policyContractRepository.save(contract);
        invalidateCustomerPolicyCaches(customerId, contract.getId());

        log.info("Cancelled policy policyId={} policyNumber={} customerId={} cancelledAt={}",
                contract.getId(), contract.getPolicyNumber(), customerId, cancellationTime);
        return contract;
    }

    public void invalidateCustomerPolicyCaches(Long customerId, Long policyId) {
        if (customerId == null || policyId == null || cacheManager == null) {
            return;
        }

        Runnable eviction = () -> {
            clearCacheIfPresent(RedisCacheConfig.POLICY_CONTRACTS_CACHE, PolicyContractReadService.policyContractListKey(customerId));
            clearCacheIfPresent(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE, PolicyContractReadService.policyContractDetailKey(customerId, policyId));
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
            return;
        }

        eviction.run();
    }

    private void clearCacheIfPresent(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evictIfPresent(key);
        }
    }

    public static String generatePolicyNumber(Long customerId, Long purchaseId) {
        if (customerId == null || purchaseId == null) {
            throw new BadRequestException("Policy number generation requires customer and purchase identity");
        }
        return "POL-" + customerId + "-" + purchaseId;
    }

    @Transactional
    public RenewalInitiationResponse initiateRenewal(Long policyContractId, Long customerId, String idempotencyKey) {
        if (policyContractId == null) {
            throw new BadRequestException("Policy contract id is required for renewal");
        }
        if (customerId == null) {
            throw new BadRequestException("Customer id is required for renewal");
        }
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);

        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyContractId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy contract", String.valueOf(policyContractId)));

        PolicyPeriod currentPeriod = contract.getCurrentPolicyPeriod();
        if (currentPeriod == null) {
            throw new BadRequestException("Policy has no current period to renew");
        }

        if (CANCELLED_POLICY_STATUS.equalsIgnoreCase(contract.getStatus()) ||
            CANCELLED_POLICY_STATUS.equalsIgnoreCase(currentPeriod.getStatus())) {
            throw new BadRequestException("Cannot renew a cancelled policy");
        }

        if (!ACTIVE_POLICY_STATUS.equalsIgnoreCase(contract.getStatus()) ||
            !ACTIVE_POLICY_STATUS.equalsIgnoreCase(currentPeriod.getStatus())) {
            throw new BadRequestException("Policy is not in an active state eligible for renewal");
        }

        // Acquire a DB-level lock on the source period to prevent concurrent renewals from racing.
        // Acquire a DB-level lock on the source period to prevent concurrent renewals from racing.
        var locked = policyPeriodRepository.findByIdForUpdate(currentPeriod.getId());
        if (locked.isPresent()) {
            currentPeriod = locked.get();
        } else {
            // If the lock could not be obtained (no row) behave as before and proceed to the existence check which will
            // either find an existing renewal or allow creation. Any lock acquisition exception will propagate — do not
            // silently downgrade to unlocked behavior.
        }

        // Re-check for an existing renewal while holding the lock (or immediately after obtaining it).
        Optional<Purchase> existingRenewal = purchaseRepository.findBySourcePolicyPeriodIdAndPurchaseTypeAndStatusIn(
                currentPeriod.getId(), PurchaseType.RENEWAL,
                java.util.Set.of(PurchaseStatus.PENDING_PAYMENT, PurchaseStatus.PAYMENT_PROCESSING));

        if (existingRenewal.isPresent()) {
            Purchase existing = existingRenewal.get();
            if (!customerId.equals(existing.getCustomerId())) {
                throw new BadRequestException("Renewal already initiated by different customer");
            }
            log.info("Renewal already in progress purchaseId={} policyContractId={} sourcePeriodId={}",
                    existing.getId(), policyContractId, currentPeriod.getId());
            return toRenewalResponse(existing);
        }

        Plan plan = loadPlan(currentPeriod.getPlanId());
        if (plan == null) {
            throw new ResourceNotFoundException("Plan", String.valueOf(currentPeriod.getPlanId()));
        }

        Long amountCents = plan.getAnnualPremiumCents();
        String currency = plan.getCurrency();
        if (amountCents == null || amountCents <= 0 || currency == null || currency.isBlank()) {
            throw new BadRequestException("Plan pricing information is unavailable for renewal");
        }

        Instant newEffectiveDate = currentPeriod.getExpirationDate();
        Instant newExpirationDate = newEffectiveDate.plus(DEFAULT_POLICY_DURATION_DAYS, ChronoUnit.DAYS);
        Instant newRenewalDate = newExpirationDate;

        Purchase renewalPurchase = Purchase.builder()
                .customerId(customerId)
                .plan(plan)
                .purchaseType(PurchaseType.RENEWAL)
                .policyContract(contract)
                .sourcePolicyPeriod(currentPeriod)
                .amountCents(amountCents)
                .currency(currency)
                .status(PurchaseStatus.PENDING_PAYMENT)
                .idempotencyKey(normalizedKey)
                .initiatedAt(Instant.now())
                .build();

        try {
            Purchase saved = purchaseRepository.saveAndFlush(renewalPurchase);
            // Defer external checkout session creation until after transaction commit
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            PolicyLifecycleService.this.checkoutHelper.finalizeCheckoutSession(saved.getId());
                        } catch (Exception e) {
                            log.error("Failed to finalize renewal checkout for purchaseId={}", saved.getId(), e);
                        }
                    }
                });
            } else {
                // No active transaction (tests): call stripePaymentGateway directly so test stubs apply
                try {
                    var checkout = stripePaymentGateway.prepareCheckoutSession(saved);
                    if (checkout.isPresent()) {
                        var session = checkout.get();
                        saved.setProviderSessionId(session.sessionId());
                        purchaseRepository.save(saved);
                    }
                } catch (Exception e) {
                    log.error("Failed to prepare checkout session synchronously for renewal purchaseId={}", saved.getId(), e);
                }
            }
            log.info("Initiated renewal purchaseId={} policyContractId={} sourcePeriodId={} planId={} amountCents={} currency={}",
                    saved.getId(), policyContractId, currentPeriod.getId(), plan.getId(), amountCents, currency);
            return toRenewalResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            Optional<Purchase> retryResult = purchaseRepository.findByIdempotencyKey(normalizedKey);
            if (retryResult.isPresent()) {
                Purchase duplicate = retryResult.get();
                if (!customerId.equals(duplicate.getCustomerId())) {
                    throw new BadRequestException("Idempotency-Key already used by a different customer");
                }
                if (!PurchaseType.RENEWAL.equals(duplicate.getPurchaseType())) {
                    throw new BadRequestException("Idempotency-Key already used for a different purchase type");
                }
                return toRenewalResponse(duplicate);
            }
            throw ex;
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new BadRequestException("Idempotency-Key header is required for renewal");
            }
            String normalized = idempotencyKey.trim();
            if (normalized.length() > 255) {
                throw new BadRequestException("Idempotency-Key is too long (max 255 characters)");
            }
            if (normalized.chars().anyMatch(c -> c <= 31)) {
                throw new BadRequestException("Idempotency-Key contains invalid characters");
            }
            return normalized;
    }

    @Transactional
    public void activateRenewal(Purchase purchase, Instant paymentConfirmedAt) {
        if (purchase == null) {
            throw new BadRequestException("Purchase is required to activate renewal");
        }
        if (!PurchaseType.RENEWAL.equals(purchase.getPurchaseType())) {
            throw new BadRequestException("Purchase is not a renewal purchase");
        }
        if (!PurchaseStatus.PAID.equals(purchase.getStatus())) {
            throw new BadRequestException("Purchase must be in PAID status to activate renewal");
        }

        PolicyContract contract = purchase.getPolicyContract();
        if (contract == null) {
            throw new BadRequestException("Renewal purchase has no associated policy contract");
        }

        PolicyPeriod sourcePeriod = purchase.getSourcePolicyPeriod();
        if (sourcePeriod == null) {
            throw new BadRequestException("Renewal purchase has no source policy period");
        }

        if (purchase.getTargetPolicyPeriod() != null) {
            log.info("Renewal already activated purchaseId={} targetPeriodId={}",
                    purchase.getId(), purchase.getTargetPolicyPeriod().getId());
            return;
        }

        Instant activationTime = paymentConfirmedAt != null ? paymentConfirmedAt : Instant.now();
        Instant newEffectiveDate = sourcePeriod.getExpirationDate();
        Instant newExpirationDate = newEffectiveDate.plus(DEFAULT_POLICY_DURATION_DAYS, ChronoUnit.DAYS);
        Instant newRenewalDate = newExpirationDate;

        Integer nextRenewalSequence = sourcePeriod.getRenewalSequence() + 1;

        PolicyPeriod newPeriod = PolicyPeriod.builder()
                .policyContract(contract)
                .previousPolicyPeriod(sourcePeriod)
                .planId(sourcePeriod.getPlanId())
                .renewalSequence(nextRenewalSequence)
                .status(ACTIVE_POLICY_STATUS)
                .effectiveDate(newEffectiveDate)
                .expirationDate(newExpirationDate)
                .renewalDate(newRenewalDate)
                .activatedAt(activationTime)
                .build();

        newPeriod = policyPeriodRepository.saveAndFlush(newPeriod);

        contract.setCurrentPolicyPeriod(newPeriod);
        contract = policyContractRepository.save(contract);

        purchase.setTargetPolicyPeriod(newPeriod);
        purchaseRepository.save(purchase);

        invalidateCustomerPolicyCaches(contract.getCustomerId(), contract.getId());

        log.info("Activated renewal purchaseId={} policyContractId={} sourcePeriodId={} targetPeriodId={} renewalSequence={} effectiveDate={} expirationDate={}",
                purchase.getId(), contract.getId(), sourcePeriod.getId(), newPeriod.getId(), nextRenewalSequence, newEffectiveDate, newExpirationDate);
    }

    private final com.claimassist.platform.policy_service.service.PurchaseServiceCheckoutHelper checkoutHelper;

    private Plan loadPlan(Long planId) {
        if (planId == null) {
            return null;
        }
        return planRepository.findById(planId).orElse(null);
    }

    private RenewalInitiationResponse toRenewalResponse(Purchase purchase) {
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
                purchase.setProviderSessionId(checkoutSessionId);
                purchaseRepository.save(purchase);
            }
        }

        return new RenewalInitiationResponse(
                purchase.getId(),
                purchase.getPolicyContract() != null ? purchase.getPolicyContract().getId() : null,
                purchase.getSourcePolicyPeriod() != null ? purchase.getSourcePolicyPeriod().getId() : null,
                purchase.getStatus().name(),
                purchase.getAmountCents(),
                purchase.getCurrency(),
                purchase.getIdempotencyKey(),
                purchase.getInitiatedAt(),
                checkoutSessionId,
                checkoutUrl,
                stripeTestMode
        );
    }

    /**
     * Determines if a policy period is currently active for coverage at the given point in time.
     * Coverage is time-derived based on the half-open interval [effectiveDate, expirationDate).
     *
     * @param period The policy period to check
     * @param asOf The point in time to check (defaults to now if null)
     * @return true if the period is active for coverage at the given time, false otherwise
     */
    public boolean isPeriodActiveAt(PolicyPeriod period, Instant asOf) {
        if (period == null) {
            return false;
        }
        Instant checkTime = asOf != null ? asOf : Instant.now();
        return (period.getEffectiveDate() != null && !period.getEffectiveDate().isAfter(checkTime)) &&
               (period.getExpirationDate() != null && period.getExpirationDate().isAfter(checkTime));
    }

    /**
     * Determines if the current policy period of a contract is actually the currently active period.
     * This validates that currentPolicyPeriod is synchronized with temporal reality.
     *
     * @param contract The policy contract to check
     * @param asOf The point in time to check (defaults to now if null)
     * @return true if currentPolicyPeriod is active at the given time, false otherwise
     */
    public boolean isCurrentPolicyPeriodActive(PolicyContract contract, Instant asOf) {
        if (contract == null || contract.getCurrentPolicyPeriod() == null) {
            return false;
        }
        return isPeriodActiveAt(contract.getCurrentPolicyPeriod(), asOf);
    }

    /**
     * Determines if a policy period has expired at the given point in time.
     * A period is expired if the current time is at or after its expirationDate.
     *
     * @param period The policy period to check
     * @param asOf The point in time to check (defaults to now if null)
     * @return true if the period has expired at the given time, false otherwise
     */
    public boolean isPeriodExpired(PolicyPeriod period, Instant asOf) {
        if (period == null || period.getExpirationDate() == null) {
            return false;
        }
        Instant checkTime = asOf != null ? asOf : Instant.now();
        return !checkTime.isBefore(period.getExpirationDate());
    }
}
