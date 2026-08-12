package com.claimassist.platform.customer_service.dto.policy;

import java.time.Instant;

/**
 * Request DTO for updating an existing policy.
 * Contains fields that are allowed to change.
 * System-managed fields (id, policyNumber, customerId, coveragePlanId) are excluded
 * as they are immutable. All fields are optional to support partial updates.
 */
public record PolicyUpdateRequest(
        String status,
        Instant renewalDate
) {}
