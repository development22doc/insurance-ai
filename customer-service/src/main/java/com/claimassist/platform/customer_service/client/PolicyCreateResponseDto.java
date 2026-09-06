package com.claimassist.platform.customer_service.client;

/**
 * DTO for Policy Service policy creation response.
 * Phase 6: Integration preparation only. Mirrors Policy Service DTO structure.
 */
public class PolicyCreateResponseDto {
    public Long policyId;
    public String policyNumber;
    public String status;
    public String stripePaymentIntentId;

    public PolicyCreateResponseDto() {}

    public PolicyCreateResponseDto(Long policyId, String policyNumber, String status, String stripePaymentIntentId) {
        this.policyId = policyId;
        this.policyNumber = policyNumber;
        this.status = status;
        this.stripePaymentIntentId = stripePaymentIntentId;
    }
}
