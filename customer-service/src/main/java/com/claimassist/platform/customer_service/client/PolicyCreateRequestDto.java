package com.claimassist.platform.customer_service.client;

import java.time.Instant;

/**
 * DTO for Policy Service policy creation request.
 * Phase 6: Integration preparation only. Mirrors Policy Service DTO structure.
 */
public class PolicyCreateRequestDto {
    public Long customerId;
    public String productCode;
    public String planCode;
    public String coverageCode;
    public Instant effectiveDate;
    public Instant renewalDate;
    public String successUrl;
    public String cancelUrl;

    public PolicyCreateRequestDto() {}

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public void setCoverageCode(String coverageCode) {
        this.coverageCode = coverageCode;
    }

    public void setEffectiveDate(Instant effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public void setRenewalDate(Instant renewalDate) {
        this.renewalDate = renewalDate;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
