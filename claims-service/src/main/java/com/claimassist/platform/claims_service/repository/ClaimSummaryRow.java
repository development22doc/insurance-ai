package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;

import java.time.Instant;

/**
 * Constructor-projection row for the "my claims" listing. Defined as a top-level type
 * (rather than a nested record inside {@link ClaimRepository}) because JPQL {@code SELECT new}
 * constructor expressions require a class that is resolvable by the JPA provider; a nested
 * record referenced by its fully-qualified name cannot be resolved at query-validation time.
 */
public record ClaimSummaryRow(
        Long id, String claimNumber, Long policyId, String incidentType, ClaimStatus status,
        Long estimatedAmountCents, Long approvedAmountCents, Instant incidentDate, Instant createdAt,
        ClaimRole role) {
}
