package com.claimassist.platform.customer_service.dto;

import java.time.Instant;

public record PolicySummaryDto(
        Long id,
        String policyNumber,
        String status,
        String productType,
        String coveragePlanName,
        Instant effectiveDate,
        Instant renewalDate
) {}