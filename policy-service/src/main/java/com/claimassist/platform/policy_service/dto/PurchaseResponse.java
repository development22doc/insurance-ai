package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PurchaseResponse(
        Long purchaseId,
        Long customerId,
        Long planId,
        Long productId,
        String planName,
        String productName,
        String status,
        Long amountCents,
        String currency,
        String idempotencyKey,
        Instant initiatedAt,
        Instant createdAt,
        String checkoutSessionId,
        String checkoutUrl,
        Boolean stripeTestMode
) {
    public PurchaseResponse(
            Long purchaseId,
            Long customerId,
            Long planId,
            Long productId,
            String planName,
            String productName,
            String status,
            Long amountCents,
            String currency,
            String idempotencyKey,
            Instant initiatedAt,
            Instant createdAt
    ) {
        this(purchaseId, customerId, planId, productId, planName, productName, status, amountCents, currency,
                idempotencyKey, initiatedAt, createdAt, null, null, false);
    }
}
