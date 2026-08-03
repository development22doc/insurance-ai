package com.claimassist.platform.common_lib.error;

import lombok.Getter;

/**
 * Thrown when a proposed claim status transition (whether requested by a
 * human adjuster via the REST API, or proposed by the AI agent via the
 * claim-update saga) violates the claim state machine - e.g. attempting to
 * move a claim from DENIED back to APPROVED without a supervisor override, or
 * skipping UNDER_REVIEW entirely. Maps to 409 CONFLICT: the request is
 * well-formed, but conflicts with the claim's current state.
 */
@Getter
public class ClaimStateTransitionException extends RuntimeException {
    private final String fromStatus;
    private final String toStatus;

    public ClaimStateTransitionException(String fromStatus, String toStatus) {
        super("Cannot transition claim from " + fromStatus + " to " + toStatus);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
}
