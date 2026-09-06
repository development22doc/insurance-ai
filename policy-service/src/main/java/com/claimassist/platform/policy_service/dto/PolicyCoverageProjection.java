package com.claimassist.platform.policy_service.dto;

public interface PolicyCoverageProjection {
    Long getPolicyId();
    String getPolicyNumber();
    String getStatus();
    String getProductType();
    String getCoveragePlanName();
    Long getDeductibleCents();
    Long getCoverageLimitCents();
    String getRenewalDate();
}
