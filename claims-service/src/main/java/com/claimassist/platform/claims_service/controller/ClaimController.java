package com.claimassist.platform.claims_service.controller;

import com.claimassist.platform.claims_service.dto.claim.ClaimRequest;
import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.dto.claim.UpdateClaimStatusRequest;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.claims_service.service.query.ClaimQueryService;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CQRS-style controller: GETs delegate to ClaimQueryService, mutating verbs
 * to ClaimCommandService. The status-update endpoint's @PreAuthorize lives
 * HERE (on the controller), not on the shared command method - see
 * ClaimCommandServiceImpl.applyStatusChange's Javadoc for why: that method is
 * also called from a Kafka listener thread with no HTTP SecurityContext for
 * @PreAuthorize's SpEL to evaluate against.
 */
@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimCommandService claimCommandService;
    private final ClaimQueryService claimQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<ClaimSummaryResponse>> getMyClaims() {
        return ResponseEntity.ok(claimQueryService.getMyClaims());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimSummaryResponse> getClaimById(@PathVariable Long id) {
        return ResponseEntity.ok(claimQueryService.getClaimById(id));
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submitClaim(
            @RequestBody @Valid ClaimRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        SubmitClaimCommand command = new SubmitClaimCommand(
                request.policyId(), request.incidentType(), request.incidentDate(),
                request.estimatedAmountCents(), currentUserProvider.getCurrentUserId(), idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(claimCommandService.submitClaim(command));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@security.canUpdateStatus(#id)")
    public ResponseEntity<ClaimSummaryResponse> updateStatus(
            @PathVariable Long id, @RequestBody @Valid UpdateClaimStatusRequest request) {

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(
                id, request.status(), request.note(), currentUserProvider.getCurrentUserId().toString());

        claimCommandService.applyStatusChange(command);
        return ResponseEntity.ok(claimQueryService.getClaimById(id));
    }
}
