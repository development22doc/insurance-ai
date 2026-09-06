package com.claimassist.platform.policy_service.controller;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    @Value("${spring.stripe.webhook-signing-secret:}")
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
                String policyNumber = pi.getMetadata().get("policy_number");
                String paymentIntentId = pi.getId();
                if (policyNumber != null) {
                    Optional<Policy> policyOpt = policyRepository.findByPolicyNumber(policyNumber);
                    if (policyOpt.isPresent()) {
                        Policy policy = policyOpt.get();
                        // idempotent: only activate if currently pending payment
                        if (!"ACTIVE".equals(policy.getStatus()) && "PENDING_PAYMENT".equals(policy.getStatus())) {
                            // Ensure stored payment intent matches (defense-in-depth)
                            if (paymentIntentId.equals(policy.getStripePaymentIntentId())) {
                                try {
                                    // Use domain transition to validate lifecycle
                                    policy.transitionTo(com.claimassist.platform.policy_service.entity.LifecycleStatus.ACTIVE);
                                    policyRepository.save(policy);
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

        // Mark event processed after successful business processing. If a concurrent insert occurs
        // the unique constraint will cause DataIntegrityViolationException and we treat that as already-processed.
        try {
            ProcessedStripeEvent marker = new ProcessedStripeEvent(eventId, eventType, Instant.now(), null);
            processedStripeEventRepository.save(marker);
        } catch (DataIntegrityViolationException dive) {
            return ResponseEntity.ok("already-processed");
        }

        return ResponseEntity.ok("received");
    }
}
