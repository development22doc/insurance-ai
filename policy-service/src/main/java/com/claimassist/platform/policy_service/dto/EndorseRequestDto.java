package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

/**
 * Endorsement request. Financial fields are snapshots only; no billing is initiated by this service.
 */
public class EndorseRequestDto {
    public String planCode; // optional: change plan within same product when permitted
    public Long deductibleCents; // optional snapshot
    public Long coverageLimitCents; // optional snapshot
    public Instant effectiveFrom; // optional
    public Instant effectiveTo; // optional
}
