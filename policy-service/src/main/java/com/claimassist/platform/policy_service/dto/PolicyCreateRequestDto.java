package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public class PolicyCreateRequestDto {
    public Long customerId;
    public String productCode;
    public String planCode;
    public String coverageCode;
    public Instant effectiveDate;
    public Instant renewalDate;
    public String successUrl;
    public String cancelUrl;
}
