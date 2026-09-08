package com.claimassist.platform.customer_service.dto.policy;

import java.time.Instant;

/**
 * Delegation DTO for policy cancellation. Mirrors Policy Service CancelRequestDto shape.
 */
public class CancelRequestDto {
    public Instant effectiveDate;
    public String reason;

    public CancelRequestDto() {}

    public CancelRequestDto(Instant effectiveDate, String reason) {
        this.effectiveDate = effectiveDate;
        this.reason = reason;
    }
}
