package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import com.claimassist.platform.policy_service.entity.ProcessedStripeEvent;

@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PolicyRepository policyRepository;
    private final com.claimassist.platform.policy_service.repository.ProcessedStripeEventRepository processedStripeEventRepository;
    private final CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Value("${stripe.webhook-signing-secret:}")
    private String webhookSigningSecret;

    @PostMapping
    @Transactional
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(name = "Stripe-Signature", required = false) String sigHeader) {
        if (webhookSigningSecret == null || webhookSigningSecret.isEmpty()) {
            // fail-closed: do not process if signing secret not configured
            return ResponseEntity.status(503).body("Webhook signing secret not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSigningSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("Invalid signature");
        }

        String eventId = event.getId();
        String eventType = event.getType();

        // Idempotency: if event already processed, short-circuit
        if (processedStripeEventRepository.existsById(eventId)) {
            return ResponseEntity.ok("already-processed");
        }

        if ("payment_intent.succeeded".equals(eventType)) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            Optional<PaymentIntent> piOptional = Optional.empty();
            if (dataObjectDeserializer.getObject().isPresent()) {
                piOptional = Optional.of((PaymentIntent) dataObjectDeserializer.getObject().get());
            }

            if (piOptional.isPresent()) {
                PaymentIntent pi = piOptional.get();
                java.util.Map<String, String> metadata = pi.getMetadata();
                String policyNumber = metadata == null ? null : metadata.get("policy_number");
                String policyIdStr = metadata == null ? null : metadata.get("policy_id");
                String paymentIntentId = pi.getId();
                if (policyNumber != null) {
                    Optional<Policy> policyOpt = policyRepository.findByPolicyNumber(policyNumber);
                    if (policyOpt.isPresent()) {
                        Policy policy = policyOpt.get();

                        // If policy_id metadata present, validate it matches the persisted policy id
                        if (policyIdStr != null && !policyIdStr.isBlank()) {
                            try {
                                long metaPolicyId = Long.parseLong(policyIdStr);
                                if (!Long.valueOf(metaPolicyId).equals(policy.getId())) {
                                    return ResponseEntity.status(400).body("Metadata policy_id does not match policy record");
                                }
                            } catch (NumberFormatException nfe) {
                                return ResponseEntity.status(400).body("Invalid metadata policy_id");
                            }
                        }

                        // idempotent: only activate if currently pending payment
                        if (!"ACTIVE".equals(policy.getStatus()) && "PENDING_PAYMENT".equals(policy.getStatus())) {
                            // Ensure stored payment intent matches (defense-in-depth)
                            if (paymentIntentId.equals(policy.getStripePaymentIntentId())) {
                                try {
                                    // Use domain transition to validate lifecycle
                                    policy.transitionTo(com.claimassist.platform.policy_service.entity.LifecycleStatus.ACTIVE);
                                    policyRepository.save(policy);
                                    evictPolicyCoverageAfterCommit(policy.getId());
                                } catch (IllegalArgumentException iae) {
                                    // Invalid transition - do not activate
                                    return ResponseEntity.status(400).body("Invalid lifecycle transition");
                                } catch (org.springframework.dao.OptimisticLockingFailureException olf) {
                                    // concurrent update lost - treat as already processed and allow Stripe retry
                                    return ResponseEntity.ok("already-processed");
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mark event processed after successful business processing. If a concurrent insert occurs        // the unique constraint will cause DataIntegrityViolationException and we treat that as already-processed.
        try {
            ProcessedStripeEvent marker = new ProcessedStripeEvent(eventId, eventType, Instant.now(), null);
            processedStripeEventRepository.save(marker);
        } catch (DataIntegrityViolationException dive) {
            return ResponseEntity.ok("already-processed");
        }

        return ResponseEntity.ok("received");
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
}
