package com.claimassist.platform.claims_service.service.command;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;

public interface ClaimCommandService {

    ClaimResponse submitClaim(SubmitClaimCommand command);

    /**
     * Shared by BOTH the human-facing REST endpoint (adjuster updates status
     * directly) AND ClaimUpdateConsumer (the AI agent's saga request) - exactly
     * one place enforces the ClaimStatus state machine, so the two entry points
     * can never drift into allowing different transitions.
     */
    Claim applyStatusChange(UpdateClaimStatusCommand command);
}
