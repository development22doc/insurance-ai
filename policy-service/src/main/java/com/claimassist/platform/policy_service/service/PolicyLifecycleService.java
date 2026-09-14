package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyLifecycleService {

    private static final String ACTIVE_POLICY_STATUS = "ACTIVE";
    private static final String CANCELLED_POLICY_STATUS = "CANCELLED";
    private static final long DEFAULT_POLICY_DURATION_DAYS = 365L;

    private final PolicyContractRepository policyContractRepository;
    private final PolicyPeriodRepository policyPeriodRepository;
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
        contract = policyContractRepository.saveAndFlush(contract);

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
        period = policyPeriodRepository.saveAndFlush(period);

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
            clearCacheIfPresent(RedisCacheConfig.CUSTOMER_POLICIES_CACHE, CustomerPolicyReadService.customerPoliciesListKey(customerId));
            clearCacheIfPresent(RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE, CustomerPolicyReadService.customerPolicyDetailKey(customerId, policyId));
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
}
