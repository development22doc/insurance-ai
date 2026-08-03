package com.claimassist.platform.claims_service.service.command;

import java.time.Instant;

public class ClaimCommands {

    public record SubmitClaimCommand(
            Long policyId, String incidentType, Instant incidentDate,
            Long estimatedAmountCents, Long submittedByUserId, String idempotencyKey
    ) {}

    public record UpdateClaimStatusCommand(
            Long claimId, String newStatus, String note, String changedBy
    ) {}

    private ClaimCommands() {}
}
