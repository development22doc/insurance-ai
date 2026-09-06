package com.claimassist.platform.claims_service.service.command.impl;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.entity.ClaimPartyId;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.mapper.ClaimMapper;
import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.claims_service.service.gateway.PolicyCoverageGateway;
import com.claimassist.platform.claims_service.support.IdempotencyService;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * CQRS command-side implementation for the Claim aggregate.
 * <p>
 * {@link #applyStatusChange} is the single, shared enforcement point for the
 * {@link ClaimStatus} state machine - both the human-facing REST endpoint
 * (see ClaimController) and {@code ClaimUpdateConsumer} (the AI agent's saga)
 * call this exact method, so there is no way for the two entry points to
 * silently diverge on what's a legal transition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ClaimCommandServiceImpl implements ClaimCommandService {

    private static final String SUBMIT_CLAIM_OPERATION = "claims.submitClaim";

    private final ClaimRepository claimRepository;
    private final ClaimPartyRepository claimPartyRepository;
    private final ClaimStatusHistoryRepository claimStatusHistoryRepository;
    private final ClaimMapper claimMapper;
    private final PolicyCoverageGateway policyCoverageGateway;
    private final IdempotencyService idempotencyService;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    @Override
    public ClaimResponse submitClaim(SubmitClaimCommand command) {
        // Idempotent by design: measure the overall business operation and emit
        // a performance event via the shared PerformanceLogger.
        long start = System.nanoTime();
        try {
            return idempotencyService.execute(
                    command.idempotencyKey(), SUBMIT_CLAIM_OPERATION, command.submittedByUserId(),
                    ClaimResponse.class, () -> doSubmitClaim(command)
            );
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            performanceLogger.log("BUSINESS", SUBMIT_CLAIM_OPERATION, elapsedMs, java.util.Map.of(
                    "policyId", command.policyId(), "submittedBy", command.submittedByUserId()
            ));
        }
    }

    private ClaimResponse doSubmitClaim(SubmitClaimCommand command) {
        // Never trust a client-supplied policyId at face value - confirm it's a
        // real, ACTIVE policy before opening a claim against it. The Policy Service
        // checks customer-scoped authorization and policy validity using the active
        // policy domain, so Claims remains a read-only client of the authoritative
        // policy source rather than using the legacy Customer implementation.
        PolicyCoverageDto policy = policyCoverageGateway.getPolicyCoverage(command.policyId(), command.submittedByUserId());
        if (!"ACTIVE".equals(policy.status())) {
            throw new BadRequestException("Cannot file a claim against a policy that is not ACTIVE (current status: " + policy.status() + ")");
        }

        Claim claim = Claim.builder()
                .claimNumber(generateClaimNumber())
                .policyId(command.policyId())
                .incidentType(command.incidentType())
                .incidentDate(command.incidentDate())
                .estimatedAmountCents(command.estimatedAmountCents())
                .status(ClaimStatus.SUBMITTED)
                .build();
        claim = claimRepository.save(claim);

        ClaimParty policyholder = ClaimParty.builder()
                .id(new ClaimPartyId(claim.getId(), command.submittedByUserId()))
                .claim(claim)
                .claimRole(ClaimRole.POLICYHOLDER)
                .build();
        claimPartyRepository.save(policyholder);

        claimStatusHistoryRepository.save(ClaimStatusHistory.builder()
                .claimId(claim.getId())
                .fromStatus("NONE")
                .toStatus(ClaimStatus.SUBMITTED.name())
                .changedBy(command.submittedByUserId().toString())
                .build());

        // Emit a structured business event for a newly submitted claim. Keep
        // details minimal and avoid PII (do not include names or tokens).
        try {
            eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                    "event", "claim.submitted",
                    "claimId", claim.getId(),
                    "claimNumber", claim.getClaimNumber(),
                    "policyId", claim.getPolicyId(),
                    "submittedBy", command.submittedByUserId()
            ));
        } catch (Exception e) {
            // Observability must not change business behavior - swallow errors here
            log.warn("Failed to emit claim.submitted event: {}", e.getMessage());
        }

        return claimMapper.toClaimResponse(claim);
    }

    /**
     * NOT annotated with @PreAuthorize on purpose: this method is called from
     * two very different contexts - an authenticated HTTP request (where
     * @PreAuthorize on the CONTROLLER method, which still has a populated
     * SecurityContext, is the right place to enforce it - see ClaimController)
     * and a Kafka listener thread (ClaimUpdateConsumer), which has no HTTP
     * request and therefore no SecurityContext for @PreAuthorize's SpEL to read
     * "the current user" from at all. Each caller is responsible for its own
     * authorization check appropriate to its context before calling this.
     */
    @Override
    @org.springframework.cache.annotation.CacheEvict(
            cacheNames = com.claimassist.platform.claims_service.config.RedisCacheConfig.CLAIM_STATUS_CACHE,
            key = "#command.claimId()")
    public Claim applyStatusChange(UpdateClaimStatusCommand command) {
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ResourceNotFoundException("Claim", command.claimId().toString()));

        ClaimStatus from = claim.getStatus();
        ClaimStatus to;
        try {
            to = ClaimStatus.valueOf(command.newStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown claim status: " + command.newStatus());
        }

        if (!from.canTransitionTo(to)) {
            throw new ClaimStateTransitionException(from.name(), to.name());
        }

        claim.setStatus(to);
        claim = claimRepository.save(claim);

        claimStatusHistoryRepository.save(ClaimStatusHistory.builder()
                .claimId(claim.getId())
                .fromStatus(from.name())
                .toStatus(to.name())
                .changedBy(command.changedBy())
                .note(command.note())
                .build());

        // Emit a structured business event for the status transition and keep
        // the human-readable log at DEBUG to avoid duplicating INFO-level
        // observability that should be consumed from the event stream.
        try {
            eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                    "event", "claim.status.changed",
                    "claimId", claim.getId(),
                    "claimNumber", claim.getClaimNumber(),
                    "from", from.name(),
                    "to", to.name(),
                    "changedBy", command.changedBy()
            ));
        } catch (Exception e) {
            log.debug("Failed to emit claim.status.changed event: {}", e.getMessage());
        }

        log.debug("Claim {} transitioned {} -> {} (by {})", claim.getClaimNumber(), from, to, command.changedBy());

        return claim;
    }

    private String generateClaimNumber() {
        return "CLM-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
