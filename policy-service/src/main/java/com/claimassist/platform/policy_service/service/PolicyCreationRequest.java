package com.claimassist.platform.policy_service.service;

import java.time.Instant;

public class PolicyCreationRequest {

    private Long customerId;
    private String productCode;
    private String planCode;
    private String coverageCode;
    private Instant effectiveDate;
    private Instant renewalDate;
    private String successUrl;
    private String cancelUrl;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public String getCoverageCode() { return coverageCode; }
    public void setCoverageCode(String coverageCode) { this.coverageCode = coverageCode; }

    public Instant getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Instant effectiveDate) { this.effectiveDate = effectiveDate; }

    public Instant getRenewalDate() { return renewalDate; }
    public void setRenewalDate(Instant renewalDate) { this.renewalDate = renewalDate; }

    public String getSuccessUrl() { return successUrl; }
    public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
}