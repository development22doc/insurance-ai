package com.claimassist.platform.policy_service.dto;

public record PlanSummaryDto(
        Long id,
        String code,
        String name,
        String status,
        Long annualPremiumCents,
        Long deductibleCents,
        Long coverageLimitCents,
        String currency
) {
}
