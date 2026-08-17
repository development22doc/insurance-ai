package com.claimassist.platform.agent_service.observability;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical, append-oriented audit event (Phase 6.15).
 * <p>
 * An audit event records a security- or business-significant action that
 * occurred during an agent request: an authorization decision, a sensitive
 * tool execution, a claim-update proposal, a security rejection, or a prompt
 * guardrail rejection. It is explicitly distinct from routine debug/log output.
 * <p>
 * Never carries secrets, raw prompts, raw tool arguments, full PII or chain of
 * thought. Identifiers are safe correlation references.
 *
 * @param auditEventId   unique audit event id (generated if not supplied).
 * @param eventType      what happened (e.g. AUTHORIZATION_DENIED, TOOL_EXECUTION,
 *                       CLAIM_UPDATE_PROPOSED, GUARDRAIL_REJECTION).
 * @param userId         safe user reference.
 * @param conversationId the conversation/session (claim) id where applicable.
 * @param requestId      the agent request id.
 * @param correlationId  the request correlation id.
 * @param tool           tool/action name when relevant.
 * @param result         outcome (e.g. ALLOWED, DENIED, ACCEPTED, REJECTED).
 * @param reason         category/reason code where appropriate.
 */
public record AuditEvent(
        String auditEventId,
        String eventType,
        Long userId,
        Long conversationId,
        String requestId,
        String correlationId,
        String tool,
        String result,
        String reason,
        Instant timestamp) {

    public static AuditEvent of(String eventType, Long userId, Long conversationId,
                                String requestId, String correlationId,
                                String tool, String result, String reason) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                userId,
                conversationId,
                requestId,
                correlationId,
                tool,
                result,
                reason,
                Instant.now());
    }
}