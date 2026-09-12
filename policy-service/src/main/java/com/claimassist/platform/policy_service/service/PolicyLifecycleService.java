package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.*;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.entity.LifecycleStatus;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PolicyLifecycleService {

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final PlanRepository planRepository;
    private final PolicyCreationService policyCreationService; // for verifyStripePayment
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Transactional
    public Policy issue(Long policyId, String suppliedPaymentIntentId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        // Only allow transition if domain permits
        if (!policy.canTransitionTo(LifecycleStatus.ACTIVE)) {
            throw new BadRequestException("Invalid lifecycle transition to ACTIVE from " + policy.getStatus());
        }

        // If already ACTIVE, idempotent noop
        if (LifecycleStatus.ACTIVE.name().equals(policy.getStatus())) {
            return policy;
        }

        String paymentIntentId = suppliedPaymentIntentId == null ? policy.getStripePaymentIntentId() : suppliedPaymentIntentId;
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new BadRequestException("No payment intent associated with policy; cannot issue without payment verification");
        }

        // Verify payment server-side and ensure PaymentIntent metadata matches this policy
        boolean paid = policyCreationService.verifyStripePaymentForPolicy(paymentIntentId, policy);
        if (!paid) {
            throw new BadRequestException("Payment not completed or does not match policy for PaymentIntent: " + paymentIntentId);
        }

        // Payment verified -> transition
        try {
            policy.transitionTo(LifecycleStatus.ACTIVE);
            Policy saved = policyRepository.save(policy);
            evictPolicyCoverageAfterCommit(policyId);
            return saved;
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(iae.getMessage());
        } catch (OptimisticLockingFailureException olf) {
            throw olf;
        }
    }

    @Transactional
    public Policy endorse(Long policyId, EndorseRequestDto req, String idempotencyKey) {
        Supplier<Map<String, Object>> command = () -> {
                    Policy policy = policyRepository.findByIdForUpdate(policyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

            if (!LifecycleStatus.ACTIVE.name().equals(policy.getStatus())) {
                throw new BadRequestException("Endorsement allowed only for ACTIVE policies");
            }

            // Determine next version number
            List<PolicyVersion> versions = policyVersionRepository.findByPolicyIdOrderByVersionNumber(policyId);
            int nextVersion = 1;
            PolicyVersion latest = null;
            if (versions != null && !versions.isEmpty()) {
                latest = versions.get(versions.size() - 1);
                nextVersion = latest.getVersionNumber() + 1;
            }

            Plan newPlan = policy.getCoveragePlan();
            if (req.planCode != null && !req.planCode.isBlank()) {
                // Validate plan belongs to same product as current plan
                Long productId = policy.getCoveragePlan().getProduct().getId();
                newPlan = planRepository.findByCodeAndProductId(req.planCode, productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Plan", req.planCode));
                // Update policy plan reference
                policy.setCoveragePlan(newPlan);
            }

            // Create snapshot version copying previous values and applying overrides
            PolicyVersion newVersion = PolicyVersion.builder()
                    .policy(policy)
                    .versionNumber(nextVersion)
                    .plan(newPlan)
                    .premiumCents(latest == null ? null : latest.getPremiumCents())
                    .deductibleCents(req.deductibleCents != null ? req.deductibleCents : (latest == null ? null : latest.getDeductibleCents()))
                    .coverageLimitCents(req.coverageLimitCents != null ? req.coverageLimitCents : (latest == null ? null : latest.getCoverageLimitCents()))
                    .effectiveFrom(req.effectiveFrom != null ? req.effectiveFrom : (latest == null ? policy.getEffectiveDate() : latest.getEffectiveFrom()))
                    .effectiveTo(req.effectiveTo != null ? req.effectiveTo : (latest == null ? null : latest.getEffectiveTo()))
                    .build();

            policyRepository.save(policy);
            policyVersionRepository.save(newVersion);

            return Map.of("policyId", policy.getId(), "versionNumber", newVersion.getVersionNumber());
        };

        // Compute fingerprint from request body for idempotency
        String fingerprint = computeFingerprint(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = idempotencyService.execute(idempotencyKey, "endorse-policy", null, fingerprint, (Class<Map<String, Object>>)(Class) Map.class, command);
        Long pid = (Number) result.get("policyId") == null ? null : ((Number) result.get("policyId")).longValue();
        if (pid == null) throw new IllegalStateException("Endorsement did not return policy id");
        Policy saved = policyRepository.findById(pid).orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(pid)));
        evictPolicyCoverageAfterCommit(pid);
        return saved;
    }

    @Transactional
    public Policy renew(Long policyId, RenewRequestDto req, String idempotencyKey) {
        Supplier<Map<String, Object>> command = () -> {
                    Policy policy = policyRepository.findByIdForUpdate(policyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

            if (!(LifecycleStatus.ACTIVE.name().equals(policy.getStatus()) || LifecycleStatus.EXPIRED.name().equals(policy.getStatus()))) {
                throw new BadRequestException("Renewal allowed only for ACTIVE or EXPIRED policies");
            }

            if (req.effectiveFrom == null) {
                throw new BadRequestException("effectiveFrom is required for renewal");
            }

            // Determine next version
            List<PolicyVersion> versions = policyVersionRepository.findByPolicyIdOrderByVersionNumber(policyId);
            int nextVersion = 1;
            PolicyVersion latest = null;
            if (versions != null && !versions.isEmpty()) {
                latest = versions.get(versions.size() - 1);
                nextVersion = latest.getVersionNumber() + 1;
            }

            Plan newPlan = policy.getCoveragePlan();
            if (req.planCode != null && !req.planCode.isBlank()) {
                Long productId = policy.getCoveragePlan().getProduct().getId();
                newPlan = planRepository.findByCodeAndProductId(req.planCode, productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Plan", req.planCode));
                policy.setCoveragePlan(newPlan);
            }

            PolicyVersion newVersion = PolicyVersion.builder()
                    .policy(policy)
                    .versionNumber(nextVersion)
                    .plan(newPlan)
                    .premiumCents(latest == null ? null : latest.getPremiumCents())
                    .deductibleCents(latest == null ? null : latest.getDeductibleCents())
                    .coverageLimitCents(latest == null ? null : latest.getCoverageLimitCents())
                    .effectiveFrom(req.effectiveFrom)
                    .effectiveTo(null)
                    .build();

            // Update policy dates
            policy.setEffectiveDate(req.effectiveFrom);
            if (req.renewalDate != null) policy.setRenewalDate(req.renewalDate);

            policyRepository.save(policy);
            policyVersionRepository.save(newVersion);

            return Map.of("policyId", policy.getId(), "versionNumber", newVersion.getVersionNumber());
        };

        String fingerprint = computeFingerprint(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = idempotencyService.execute(idempotencyKey, "renew-policy", null, fingerprint, (Class<Map<String, Object>>)(Class) Map.class, command);
        Long pid = (Number) result.get("policyId") == null ? null : ((Number) result.get("policyId")).longValue();
        if (pid == null) throw new IllegalStateException("Renewal did not return policy id");
        Policy saved = policyRepository.findById(pid).orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(pid)));
        evictPolicyCoverageAfterCommit(pid);
        return saved;
    }

    @Transactional
    public Policy cancel(Long policyId, CancelRequestDto req) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        if (!policy.canTransitionTo(LifecycleStatus.CANCELLED)) {
            throw new BadRequestException("Invalid lifecycle transition to CANCELLED from " + policy.getStatus());
        }

        try {
            policy.transitionTo(LifecycleStatus.CANCELLED);
            Policy saved = policyRepository.save(policy);
            evictPolicyCoverageAfterCommit(policyId);
            return saved;
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(iae.getMessage());
        }
    }

    @Transactional
    public Policy reinstate(Long policyId, ReinstateRequestDto req, Long currentUserId, String idempotencyKey) {
            String paymentIntentId = req == null ? null : req.stripePaymentIntentId;

            // If payment intent supplied, require idempotency and execute the full mutation path under idempotency protection
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                    throw new BadRequestException("Idempotency-Key header is required for payment-backed reinstatement");
                }

                Supplier<Map<String, Object>> command = () -> {
                    Policy policy = policyRepository.findByIdForUpdate(policyId)
                            .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

                    // Allowed source statuses per domain
                    if (!(LifecycleStatus.CANCELLED.name().equals(policy.getStatus()) || LifecycleStatus.EXPIRED.name().equals(policy.getStatus()))) {
                        throw new BadRequestException("Reinstate is allowed only from CANCELLED or EXPIRED");
                    }

                    // Move to REINSTATEMENT_PENDING if not already
                    if (!LifecycleStatus.REINSTATEMENT_PENDING.name().equals(policy.getStatus())) {
                        policy.transitionTo(LifecycleStatus.REINSTATEMENT_PENDING);
                        policyRepository.save(policy);
                    }

                    // Verify payment server-side and ensure PaymentIntent metadata matches this policy
                    boolean paid = policyCreationService.verifyStripePaymentForPolicy(paymentIntentId, policy);
                    if (!paid) {
                        throw new BadRequestException("Payment not completed or does not match policy for PaymentIntent: " + paymentIntentId);
                    }

                    // Create new PolicyVersion on activation
                    List<PolicyVersion> versions = policyVersionRepository.findByPolicyIdOrderByVersionNumber(policyId);
                    int nextVersion = 1;
                    PolicyVersion latest = null;
                    if (versions != null && !versions.isEmpty()) {
                        latest = versions.get(versions.size() - 1);
                        nextVersion = latest.getVersionNumber() + 1;
                    }

                    PolicyVersion newVersion = PolicyVersion.builder()
                            .policy(policy)
                            .versionNumber(nextVersion)
                            .plan(policy.getCoveragePlan())
                            .premiumCents(latest == null ? null : latest.getPremiumCents())
                            .deductibleCents(latest == null ? null : latest.getDeductibleCents())
                            .coverageLimitCents(latest == null ? null : latest.getCoverageLimitCents())
                            .effectiveFrom(policy.getEffectiveDate())
                            .effectiveTo(null)
                            .build();

                    policy.transitionTo(LifecycleStatus.ACTIVE);
                    policyRepository.save(policy);
                    policyVersionRepository.save(newVersion);

                    return Map.of("policyId", policy.getId(), "status", policy.getStatus());
                };

                // Compute fingerprint including policyId and payment intent
                String fingerprint = computeFingerprint(Map.of("policyId", policyId, "stripePaymentIntentId", paymentIntentId));
                @SuppressWarnings("unchecked")
                Map<String, Object> result = idempotencyService.execute(idempotencyKey, "reinstate-policy", currentUserId, fingerprint, (Class<Map<String, Object>>)(Class) Map.class, command);
                Long pid = (Number) result.get("policyId") == null ? null : ((Number) result.get("policyId")).longValue();
                if (pid == null) throw new IllegalStateException("Reinstate did not return policy id");
                Policy saved = policyRepository.findById(pid).orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(pid)));
                evictPolicyCoverageAfterCommit(pid);
                return saved;
            }

            // No-payment path: preserve existing behavior (move to REINSTATEMENT_PENDING)
            Policy policy = policyRepository.findByIdForUpdate(policyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

            // Allowed source statuses per domain
            if (!(LifecycleStatus.CANCELLED.name().equals(policy.getStatus()) || LifecycleStatus.EXPIRED.name().equals(policy.getStatus()))) {
                throw new BadRequestException("Reinstate is allowed only from CANCELLED or EXPIRED");
            }

            // Move to REINSTATEMENT_PENDING (idempotent if already in that state)
            if (!LifecycleStatus.REINSTATEMENT_PENDING.name().equals(policy.getStatus())) {
                policy.transitionTo(LifecycleStatus.REINSTATEMENT_PENDING);
                policyRepository.save(policy);
            }

            Policy saved = policyRepository.findById(policyId).orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));
            evictPolicyCoverageAfterCommit(policyId);
            return saved;
        }

    private void evictPolicyCoverageAfterCommit(Long policyId) {
        Runnable evictTask = () -> {
            Cache cache = cacheManager.getCache(RedisCacheConfig.POLICY_COVERAGE_CACHE);
            if (cache == null || policyId == null) {
                return;
            }
            try {
                cache.evict(policyId);
            } catch (Exception e) {
                System.err.println("Failed to evict policy coverage cache key " + policyId + ": " + e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictTask.run();
                }
            });
        } else {
            evictTask.run();
        }
    }

    private String computeFingerprint(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute fingerprint", e);
        }
    }
}
