package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PlanDto(
        Long id,
        String code,
        String name,
        Boolean active,
        Long productId,
        String productCode,
        Long deductibleCents,
        Long coverageLimitCents,
        Instant createdAt
) {}
