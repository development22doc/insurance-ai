package com.claimassist.platform.common_lib.enums;

/**
 * Only meaningful for CLAIM_UPDATE_PROPOSED events - everything else is
 * created already CONFIRMED since it never goes through the saga.
 */
public enum AgentEventStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
