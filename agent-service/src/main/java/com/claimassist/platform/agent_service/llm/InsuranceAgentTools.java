package com.claimassist.platform.agent_service.llm;

import com.claimassist.platform.agent_service.ai.tool.ToolMetadata;
import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import com.claimassist.platform.agent_service.observability.AuditEvent;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The agent's ENTIRE surface for touching real backend insurance data.
 * <p>
 * Instantiated fresh per chat turn (see AgentGenerationServiceImpl), not a
 * Spring singleton, because it's scoped to one specific claimId for one
 * specific request - a singleton bean would leak claim context across
 * concurrent users' conversations.
 * <p>
 * Phase 2 hardens every tool so the LLM can never bypass application policy:
 * <ul>
 *   <li><b>Input validation</b> - arguments (and the request-scoped claim/policy
 *       ids) are validated before any backend call; invalid input returns a
 *       structured {@code INVALID_TOOL_ARGUMENTS} failure and the backend is
 *       never reached.</li>
 *   <li><b>Authorization</b> - each tool checks its {@link ToolMetadata}
 *       required permission against the claim via the existing, fail-closed
 *       ClaimsServiceGateway. The LLM does not decide authorization; a denied
 *       user gets a structured {@code UNAUTHORIZED} failure and the backend is
 *       never reached.</li>
 *   <li><b>Risk classification</b> - READ tools only fetch; the WRITE tool is
 *       a proposal that never mutates claim state directly (it hands off to a
 *       callback that enqueues a saga request, validated independently by
 *       claims-service).</li>
 *   <li><b>Idempotency</b> - proposing the same target status twice in one
 *       turn is collapsed to a single side effect.</li>
 *   <li><b>Structured output + provenance</b> - every tool returns a
 *       {@link ToolResult} JSON contract with an explicit success/failure,
 *       error code, retryability and a source label.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class InsuranceAgentTools {

    private static final String NOT_FOUND = "NOT_FOUND";

    /** Statuses the model may propose (validated here, re-validated by claims-service). */
    private static final Set<String> ALLOWED_PROPOSED_STATUSES = Set.of(
            "UNDER_REVIEW", "DOCS_REQUESTED", "APPROVED", "DENIED", "PAID", "CLOSED");

    private final Long claimId;
    private final Long policyId;
    private final Long userId;
    private final ClaimsServiceGateway claimsServiceGateway;
    private final CustomerServiceGateway customerServiceGateway;
    private final ToolRegistry registry;

    /**
     * Maximum accepted length of a proposed-update note, so an oversized value
     * can never reach the claim's permanent audit trail. Configurable via
     * {@code agent.ai.max-note-length}.
     */
    private final int maxNoteLength;

    /** Invoked by AgentGenerationServiceImpl to receive a proposed update and enqueue the saga. */
    private final Consumer<ProposedUpdate> onProposedUpdate;

    /** Idempotency guard: statuses already proposed this turn, to collapse duplicates. */
    private final Set<String> proposedStatuses = ConcurrentHashMap.newKeySet();

    /** Optional observability/audit sink (Phase 6); never required for business logic. */
    private AgentTelemetry agentTelemetry;

    /** Correlation ids, passed through to telemetry so tool events are traceable. */
    private final String requestId;
    private final String correlationId;

    public record ProposedUpdate(String proposedStatus, String note) {}

    /** Convenience constructor without telemetry (kept for backward compatibility). */
    public InsuranceAgentTools(Long claimId, Long policyId, Long userId, ClaimsServiceGateway claimsServiceGateway,
                               CustomerServiceGateway customerServiceGateway, ToolRegistry registry,
                               int maxNoteLength, Consumer<ProposedUpdate> onProposedUpdate) {
        this(claimId, policyId, userId, claimsServiceGateway, customerServiceGateway, registry,
                maxNoteLength, onProposedUpdate, null, "", "");
    }

    /** Constructor with an optional telemetry sink for security/audit observability. */
    public InsuranceAgentTools(Long claimId, Long policyId, Long userId, ClaimsServiceGateway claimsServiceGateway,
                               CustomerServiceGateway customerServiceGateway, ToolRegistry registry,
                               int maxNoteLength, Consumer<ProposedUpdate> onProposedUpdate,
                               @Nullable AgentTelemetry agentTelemetry,
                               String requestId, String correlationId) {
        this.claimId = claimId;
        this.policyId = policyId;
        this.userId = userId;
        this.claimsServiceGateway = claimsServiceGateway;
        this.customerServiceGateway = customerServiceGateway;
        this.registry = registry;
        this.maxNoteLength = maxNoteLength;
        this.onProposedUpdate = onProposedUpdate;
        this.agentTelemetry = agentTelemetry;
        this.requestId = requestId == null ? "" : requestId;
        this.correlationId = correlationId == null ? "" : correlationId;
    }

    @Tool(name = "get_claim_status",
            description = "Get the current status of this claim, including its full status change history. " +
                    "ALWAYS call this before telling the customer their claim status - never guess or rely on prior turns.")
    public String getClaimStatus() {
        if (isInvalidId(claimId)) {
            return invalid("claimId");
        }
        ToolMetadata meta = require(ToolRegistry.CLAIM_STATUS);
        if (isDenied(meta)) {
            return unauthorized().toJson();
        }
        try {
            ClaimStatusDto status = claimsServiceGateway.getClaimStatus(claimId);
            log.info("Tool call: get_claim_status(claimId={})", claimId);
            if (status == null || NOT_FOUND.equals(status.status())) {
                return ToolResult.failure("CLAIM_NOT_FOUND", false,
                        "No claim was found for this account.").toJson();
            }
            return ToolResult.success(status, meta.source()).toJson();
        } catch (Exception e) {
            log.warn("get_claim_status failed for claim {}: {}", claimId, e.toString());
            return ToolResult.failure("CLAIMS_SERVICE_UNAVAILABLE", true,
                    "Unable to retrieve the claim status right now.").toJson();
        }
    }

    @Tool(name = "get_policy_coverage",
            description = "Get this claim's underlying policy: deductible, coverage limit, product type, and status. " +
                    "ALWAYS call this before stating any coverage amount, deductible, or limit - never state a number from memory.")
    public String getPolicyCoverage() {
        if (isInvalidId(policyId)) {
            return invalid("policyId");
        }
        ToolMetadata meta = require(ToolRegistry.POLICY_COVERAGE);
        if (isDenied(meta)) {
            return unauthorized().toJson();
        }
        try {
            PolicyCoverageDto coverage = customerServiceGateway.getPolicyCoverage(policyId, userId);
            log.info("Tool call: get_policy_coverage(policyId={}, userId={})", policyId, userId);
            if (coverage == null || NOT_FOUND.equals(coverage.status())) {
                return ToolResult.failure("POLICY_NOT_FOUND", false,
                        "No policy was found for this account.").toJson();
            }
            return ToolResult.success(coverage, meta.source()).toJson();
        } catch (Exception e) {
            log.warn("get_policy_coverage failed for policy {}: {}", policyId, e.toString());
            return ToolResult.failure("CUSTOMER_SERVICE_UNAVAILABLE", true,
                    "Unable to retrieve the policy coverage right now.").toJson();
        }
    }

    @Tool(name = "get_claim_documents",
            description = "Get the list of documents already submitted for this claim, including OCR-extracted text " +
                    "and fraud-signal scores where available. Use this to summarize what's been submitted, or to check " +
                    "whether required documents (e.g. a police report) are still missing before proposing DOCS_REQUESTED.")
    public String getClaimDocuments() {
        if (isInvalidId(claimId)) {
            return invalid("claimId");
        }
        ToolMetadata meta = require(ToolRegistry.CLAIM_DOCUMENTS);
        if (isDenied(meta)) {
            return unauthorized().toJson();
        }
        try {
            List<ClaimDocumentSummaryDto> docs = claimsServiceGateway.getClaimDocuments(claimId);
            log.info("Tool call: get_claim_documents(claimId={}) -> {} document(s)", claimId, docs.size());
            return ToolResult.success(docs, meta.source()).toJson();
        } catch (Exception e) {
            log.warn("get_claim_documents failed for claim {}: {}", claimId, e.toString());
            return ToolResult.failure("CLAIMS_SERVICE_UNAVAILABLE", true,
                    "Unable to retrieve the claim documents right now.").toJson();
        }
    }

    @Tool(name = "propose_claim_update",
            description = "Propose a claim status change. This does NOT apply the change directly - it is validated " +
                    "independently by claims-service's own state machine and permission check, and may be rejected. " +
                    "Valid target statuses: UNDER_REVIEW, DOCS_REQUESTED, APPROVED, DENIED, PAID, CLOSED. " +
                    "Only propose APPROVED/DENIED if you have clear, explicit instruction from an adjuster in this " +
                    "conversation - never propose a coverage decision on your own initiative.")
    public String proposeClaimUpdate(
            @ToolParam(description = "The target status, e.g. 'DOCS_REQUESTED'") String proposedStatus,
            @ToolParam(description = "A short note explaining why, shown in the claim's permanent audit trail") String note
    ) {
        ToolMetadata meta = require(ToolRegistry.PROPOSE_CLAIM_UPDATE);
        if (proposedStatus == null || proposedStatus.isBlank()) {
            return invalid("proposedStatus");
        }
        String normalizedStatus = proposedStatus.trim().toUpperCase();
        if (!ALLOWED_PROPOSED_STATUSES.contains(normalizedStatus)) {
            return ToolResult.failure("INVALID_TOOL_ARGUMENTS", false,
                    "The requested status is not a valid target status.").toJson();
        }
        if (isDenied(meta)) {
            return unauthorized().toJson();
        }
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > maxNoteLength) {
            return ToolResult.failure("INVALID_TOOL_ARGUMENTS", false,
                    "The note is too long. Please keep it under " + maxNoteLength + " characters.").toJson();
        }

        // Idempotency: the same logical proposal (same target status) in this
        // turn produces exactly one saga/enqueue side effect.
        if (!proposedStatuses.add(normalizedStatus)) {
            log.info("Tool call: propose_claim_update(claimId={}, proposedStatus={}) already proposed - ignored",
                    claimId, normalizedStatus);
            return ToolResult.success(Map.of(
                            "proposedStatus", normalizedStatus,
                            "note", safeNote,
                            "idempotent", true),
                    meta.source()).toJson();
        }

        log.info("Tool call: propose_claim_update(claimId={}, proposedStatus={})", claimId, normalizedStatus);
        onProposedUpdate.accept(new ProposedUpdate(normalizedStatus, safeNote));
        // The write is accepted by the existing business workflow (enqueues the
        // saga). After it is accepted, invalidate the claim's read caches so the
        // next status/documents read is fresh. Cache is never used for the write.
        claimsServiceGateway.evictClaimStatus(claimId);
        if (agentTelemetry != null) {
            agentTelemetry.audit(AuditEvent.of("CLAIM_UPDATE_PROPOSED", null, claimId,
                    requestId, correlationId, "propose_claim_update", "ACCEPTED", normalizedStatus));
        }
        return ToolResult.success(Map.of("proposedStatus", normalizedStatus, "note", safeNote),
                meta.source()).toJson();
    }

    private boolean isInvalidId(Long id) {
        return id == null || id <= 0;
    }

    private ToolMetadata require(String name) {
        return registry.metadata(name)
                .orElseThrow(() -> new IllegalStateException("No metadata registered for tool " + name));
    }

    /** True when the current user does NOT hold this tool's required permission for the claim. */
    private boolean isDenied(ToolMetadata meta) {
        // Fail-closed: if the permission check itself errors (e.g. the authz
        // service is down), we DENY rather than risk reaching the backend.
        boolean denied;
        try {
            denied = !claimsServiceGateway.checkPermission(claimId, meta.requiredPermission());
        } catch (Exception e) {
            log.warn("Permission check failed for claim {} tool {} - denying: {}",
                    claimId, meta.name(), e.toString());
            denied = true;
        }
        if (denied && agentTelemetry != null) {
            agentTelemetry.securityDenied(requestId, correlationId, claimId, null,
                    meta.name(), "AUTHORIZATION_DENIED");
            agentTelemetry.audit(AuditEvent.of("AUTHORIZATION_DENIED", null, claimId,
                    requestId, correlationId, meta.name(), "DENIED", "UNAUTHORIZED"));
        }
        return denied;
    }

    private ToolResult unauthorized() {
        return ToolResult.failure("UNAUTHORIZED", false,
                "You are not authorized to perform that action.");
    }

    private String invalid(String field) {
        return ToolResult.failure("INVALID_TOOL_ARGUMENTS", false,
                "A valid " + field + " is required to run this tool.").toJson();
    }
}