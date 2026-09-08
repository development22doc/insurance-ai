package com.claimassist.platform.policy_service.dto;

public class PolicyCreateResponseDto {
    public Long policyId;
    public String policyNumber;
    public String status;
    public String stripePaymentIntentId;
    public String clientSecret;
    public Long amount;
    public String currency;

    public PolicyCreateResponseDto() {}

    public PolicyCreateResponseDto(Long policyId, String policyNumber, String status, String stripePaymentIntentId, String clientSecret, Long amount, String currency) {
        this.policyId = policyId;
        this.policyNumber = policyNumber;
        this.status = status;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.clientSecret = clientSecret;
        this.amount = amount;
        this.currency = currency;
    }
}
