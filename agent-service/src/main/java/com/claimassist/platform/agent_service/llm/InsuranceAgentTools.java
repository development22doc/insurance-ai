package com.claimassist.platform.agent_service.llm;

import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.function.Consumer;

/**
 * The agent's ENTIRE surface for touching real backend insurance data.
 * <p>
 * Instantiated fresh per chat turn (see AgentGenerationServiceImpl), not a
 * Spring singleton, because it's scoped to one specific claimId for one
 * specific request - same reasoning as CodeGenerationTools in the Lovable
 * clone's read_files tool: a singleton bean would leak claim context across
 * concurrent users' conversations.
 * <p>
 * Three tools are pure reads, grounding the model in real data instead of
 * letting it guess. The fourth, {@link #proposeClaimUpdate}, NEVER mutates
 * anything directly - it hands off to a callback that enqueues a saga request
 * (see AgentGenerationServiceImpl). This is the single most important design
 * constraint in this whole platform: there is no code path where an LLM
 * tool-call result becomes a database write without going through
 * claims-service's own independent state-machine validation first.
 */
@RequiredArgsConstructor
@Slf4j
public class InsuranceAgentTools {

    private final Long claimId;
    private final Long policyId;
    private final ClaimsServiceGateway claimsServiceGateway;
    private final CustomerServiceGateway customerServiceGateway;

    /** Invoked by AgentGenerationServiceImpl to receive a proposed update and enqueue the saga. */
    private final Consumer<ProposedUpdate> onProposedUpdate;

    public record ProposedUpdate(String proposedStatus, String note) {}

    @Tool(name = "get_claim_status",
            description = "Get the current status of this claim, including its full status change history. " +
                    "ALWAYS call this before telling the customer their claim status - never guess or rely on prior turns.")
    public String getClaimStatus() {
        ClaimStatusDto status = claimsServiceGateway.getClaimStatus(claimId);
        log.info("Tool call: get_claim_status(claimId={})", claimId);
        return status.toString();
    }

    @Tool(name = "get_policy_coverage",
            description = "Get this claim's underlying policy: deductible, coverage limit, product type, and status. " +
                    "ALWAYS call this before stating any coverage amount, deductible, or limit - never state a number from memory.")
    public String getPolicyCoverage() {
        PolicyCoverageDto coverage = customerServiceGateway.getPolicyCoverage(policyId);
        log.info("Tool call: get_policy_coverage(policyId={})", policyId);
        return coverage.toString();
    }

    @Tool(name = "get_claim_documents",
            description = "Get the list of documents already submitted for this claim, including OCR-extracted text " +
                    "and fraud-signal scores where available. Use this to summarize what's been submitted, or to check " +
                    "whether required documents (e.g. a police report) are still missing before proposing DOCS_REQUESTED.")
    public String getClaimDocuments() {
        List<ClaimDocumentSummaryDto> docs = claimsServiceGateway.getClaimDocuments(claimId);
        log.info("Tool call: get_claim_documents(claimId={}) -> {} document(s)", claimId, docs.size());
        return docs.toString();
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
        log.info("Tool call: propose_claim_update(claimId={}, proposedStatus={})", claimId, proposedStatus);
        onProposedUpdate.accept(new ProposedUpdate(proposedStatus, note));
        return "Update proposed and queued for validation. It is NOT yet applied - you will be informed of the outcome.";
    }
}
