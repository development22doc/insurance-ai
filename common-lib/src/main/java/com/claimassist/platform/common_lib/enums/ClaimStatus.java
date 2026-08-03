package com.claimassist.platform.common_lib.enums;

import java.util.Map;
import java.util.Set;

/**
 * The claim state machine. Enforced centrally here (both claims-service's
 * command handler AND its Kafka saga consumer call isValidTransition before
 * applying ANY status change) so there is exactly one place that decides
 * what's a legal transition - not scattered across "the REST endpoint's
 * validation" and "the saga consumer's validation" as two versions that could
 * drift apart.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    DOCS_REQUESTED,
    APPROVED,
    DENIED,
    PAID,
    CLOSED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED_TRANSITIONS = Map.of(
            SUBMITTED, Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(DOCS_REQUESTED, APPROVED, DENIED),
            DOCS_REQUESTED, Set.of(UNDER_REVIEW),
            APPROVED, Set.of(PAID),
            DENIED, Set.of(CLOSED),
            PAID, Set.of(CLOSED),
            CLOSED, Set.of()
    );

    public boolean canTransitionTo(ClaimStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
