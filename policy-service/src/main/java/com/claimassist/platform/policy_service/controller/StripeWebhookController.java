package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.policy_service.entity.PaymentEvent;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.repository.PaymentEventRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import com.claimassist.platform.policy_service.service.PurchaseService;
import com.claimassist.platform.policy_service.service.StripePaymentGateway;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripePaymentGateway stripePaymentGateway;
    private final PurchaseRepository purchaseRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PurchaseService purchaseService;

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("Empty Stripe webhook payload");
        }
        if (payload.length() > 200_000) {
            throw new BadRequestException("Stripe webhook payload too large");
        }

        String signaturePresence = stripeSignature == null || stripeSignature.isBlank() ? "missing" : "present";
        Event event;
        try {
            event = stripePaymentGateway.verifyAndParseWebhook(payload, stripeSignature);
        } catch (BadRequestException ex) {
            log.warn("Stripe webhook verification failed signature={} reason={}", signaturePresence, ex.getMessage());
            throw ex;
        }

        String eventType = event.getType();
        if (paymentEventRepository.findByProviderEventId(event.getId()).isPresent()) {
            log.info("Stripe webhook duplicate eventId={} eventType={} status=duplicate", event.getId(), eventType);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "duplicate"
            ));
        }

        Long purchaseId = extractPurchaseId(event);
        if (purchaseId == null) {
            log.warn("Stripe webhook ignored eventId={} eventType={} status=ignored reason=no_purchase_metadata", event.getId(), eventType);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "ignored"
            ));
        }

        Purchase purchase = purchaseRepository.findById(purchaseId).orElse(null);
        if (purchase == null) {
            log.warn("Stripe webhook ignored eventId={} eventType={} purchaseId={} status=ignored reason=purchase_not_found", event.getId(), eventType, purchaseId);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "ignored"
            ));
        }

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .purchase(purchase)
                .providerEventId(event.getId())
                .eventType(eventType)
                .eventStatus("RECEIVED")
                .payload(payload)
                .processedAt(Instant.now())
                .build();

        try {
            paymentEventRepository.save(paymentEvent);
        } catch (DataIntegrityViolationException ex) {
            log.info("Stripe webhook duplicate eventId={} eventType={} purchaseId={} status=duplicate reason=payment_event_conflict", event.getId(), eventType, purchaseId);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "duplicate"
            ));
        }

        if (isHandledEventType(eventType)) {
            log.info("Stripe webhook applying eventId={} eventType={} purchaseId={} status=processing", event.getId(), eventType, purchaseId);
            purchaseService.applyWebhookState(purchase.getId(), eventType);
            paymentEvent.setEventStatus("APPLIED");
            try {
                paymentEventRepository.save(paymentEvent);
            } catch (DataIntegrityViolationException ex) {
                log.info("Stripe webhook duplicate eventId={} eventType={} purchaseId={} status=duplicate reason=apply_conflict", event.getId(), eventType, purchaseId);
                return ResponseEntity.ok(Map.of(
                        "received", true,
                        "eventId", event.getId(),
                        "status", "duplicate"
                ));
            }
            log.info("Stripe webhook accepted eventId={} eventType={} purchaseId={} status=accepted", event.getId(), eventType, purchaseId);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "accepted"
            ));
        }

        paymentEvent.setEventStatus("IGNORED");
        try {
            paymentEventRepository.save(paymentEvent);
        } catch (DataIntegrityViolationException ex) {
            log.info("Stripe webhook duplicate eventId={} eventType={} purchaseId={} status=duplicate reason=ignored_conflict", event.getId(), eventType, purchaseId);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "eventId", event.getId(),
                    "status", "duplicate"
            ));
        }
        log.info("Stripe webhook ignored eventId={} eventType={} purchaseId={} status=ignored", event.getId(), eventType, purchaseId);
        return ResponseEntity.ok(Map.of(
                "received", true,
                "eventId", event.getId(),
                "status", "ignored"
        ));
    }

    private boolean isHandledEventType(String eventType) {
        return eventType != null && (eventType.equals("checkout.session.completed")
                || eventType.equals("checkout.session.async_payment_started")
                || eventType.equals("payment_intent.processing")
                || eventType.equals("payment_intent.succeeded")
                || eventType.equals("checkout.session.async_payment_succeeded")
                || eventType.equals("charge.succeeded")
                || eventType.equals("payment_intent.payment_failed")
                || eventType.equals("checkout.session.async_payment_failed")
                || eventType.equals("charge.failed")
                || eventType.equals("checkout.session.expired")
                || eventType.equals("checkout.session.cancelled"));
    }

    private Long extractPurchaseId(Event event) {
        try {
            Object dataObject = event.getData().getObject();
            if (dataObject instanceof Session session) {
                String purchaseId = session.getMetadata() != null ? session.getMetadata().get("purchaseId") : null;
                if (purchaseId != null && !purchaseId.isBlank()) {
                    return Long.parseLong(purchaseId);
                }
            }
            if (dataObject instanceof PaymentIntent paymentIntent) {
                String purchaseId = paymentIntent.getMetadata() != null ? paymentIntent.getMetadata().get("purchaseId") : null;
                if (purchaseId != null && !purchaseId.isBlank()) {
                    return Long.parseLong(purchaseId);
                }
            }
            return null;
        } catch (Exception ex) {
            throw new BadRequestException("Malformed Stripe event payload");
        }
    }
}
