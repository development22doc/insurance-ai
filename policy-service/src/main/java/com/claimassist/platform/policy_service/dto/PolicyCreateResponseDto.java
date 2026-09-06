package com.claimassist.platform.policy_service.dto;

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
