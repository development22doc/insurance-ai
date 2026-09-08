package com.claimassist.platform.customer_service.dto.policy;

/**
 * Delegation DTO for policy reinstatement. Mirrors Policy Service ReinstateRequestDto shape.
 */
public class ReinstateRequestDto {
    public String stripePaymentIntentId;

    public ReinstateRequestDto() {}

    public ReinstateRequestDto(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }
}
