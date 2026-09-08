package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

/**
 * Renewal request. Billing is out-of-scope; this API snapshots new term and updates policy dates.
 */
public class RenewRequestDto {
    public Instant effectiveFrom;
    public Instant renewalDate;
    public String planCode; // optional
}
