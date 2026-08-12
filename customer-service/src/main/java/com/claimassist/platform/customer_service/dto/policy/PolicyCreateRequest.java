package com.claimassist.platform.customer_service.dto.policy;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Request DTO for creating a new policy.
 * Only contains fields that the client is allowed to provide when creating a policy.
 * System-managed fields (id, policyNumber, status, customerId) are excluded
 * to prevent unauthorized modifications.
 */
public record PolicyCreateRequest(
        @NotNull(message = "coveragePlanId is required")
        Long coveragePlanId,

        @NotNull(message = "effectiveDate is required")
        Instant effectiveDate,

        Instant renewalDate
) {}
