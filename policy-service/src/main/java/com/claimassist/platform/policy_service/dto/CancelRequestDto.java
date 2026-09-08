package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

/**
 * Cancellation request. effectiveDate and reason are informational; persistence for reason is not supported by current model.
 */
public class CancelRequestDto {
    public Instant effectiveDate;
    public String reason;
}
