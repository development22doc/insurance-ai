package com.claimassist.platform.policy_service.dto;

/**
 * Reinstatement request. Optional stripePaymentIntentId may be provided to attempt immediate activation subject to server-side verification.
 */
public class ReinstateRequestDto {
    public String stripePaymentIntentId;
}
