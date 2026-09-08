package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PolicyVersionDto(
        Long id,
        Integer versionNumber,
        Long planId,
        String planCode,
        String planName,
        Long premiumCents,
        Long deductibleCents,
        Long coverageLimitCents,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt
) {}
