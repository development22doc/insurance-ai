package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record RenewalInitiationResponse(
        Long purchaseId,
        Long policyContractId,
        Long sourcePolicyPeriodId,
        String status,
        Long amountCents,
        String currency,
        String idempotencyKey,
        Instant initiatedAt,
        String checkoutSessionId,
        String checkoutUrl,
        Boolean stripeTestMode
) {
}
