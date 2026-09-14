package com.claimassist.platform.policy_service.dto;

public record PlanDetailDto(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String code,
        String name,
        String status,
        Long annualPremiumCents,
        Long deductibleCents,
        Long coverageLimitCents,
        String currency
) {
}
