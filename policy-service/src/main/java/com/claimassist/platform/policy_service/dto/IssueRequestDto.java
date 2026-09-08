package com.claimassist.platform.policy_service.dto;

/**
 * Request body for issuing a policy. PaymentIntent id optional - server will verify if provided.
 */
public class IssueRequestDto {
    public String stripePaymentIntentId;
}
