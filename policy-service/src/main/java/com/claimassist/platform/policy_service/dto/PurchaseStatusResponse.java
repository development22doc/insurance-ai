package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PurchaseStatusResponse(
        Long purchaseId,
        Long customerId,
        Long planId,
        Long productId,
        String status,
        Long amountCents,
        String currency,
        Instant initiatedAt,
        Instant createdAt
) {
}
