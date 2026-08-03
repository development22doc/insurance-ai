package com.claimassist.platform.common_lib.event;

/**
 * Published by agent-service (via its Outbox) when the AI agent's
 * propose_claim_update tool is invoked. NEVER applied directly - claims-service
 * consumes this, re-validates against the ClaimStatus state machine
 * independently of whatever the LLM asked for, and only then applies it.
 */
public record ClaimUpdateRequestEvent(
        Long claimId,
        String sagaId,
        String proposedStatus,
        String note,
        Long proposedByUserId
) {}
