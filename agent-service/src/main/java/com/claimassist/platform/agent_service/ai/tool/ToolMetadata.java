package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.common_lib.enums.ClaimPermission;

/**
 * Immutable, application-side metadata describing one agent tool.
 * <p>
 * This is deliberately separate from Spring AI's {@code ToolDefinition}
 * (which drives LLM tool selection). The registry holds the operational
 * policy that agent-service's own code enforces around each tool:
 * risk level, required permission, timeout, retryability, idempotency and
 * provenance. It exists so authorization / risk / timeout decisions live in
 * one testable catalog instead of being scattered across tool bodies.
 *
 * @param name                Spring AI tool name (must match the {@code @Tool} method name).
 * @param description         human-readable purpose (for audit/catalog).
 * @param riskLevel           READ or WRITE classification.
 * @param requiredPermission  permission a user must hold (against the claim) to invoke this tool.
 * @param timeoutMs           0 = use the global {@code agent.ai.tool-timeout-ms} default.
 * @param maxRetries          application-level retry budget; actual transient retries are owned by
 *                            the resilient gateways (Resilience4j), so this is 0 at the tool layer to
 *                            avoid duplicate/conflicting retries. 0 also for any non-idempotent write.
 * @param idempotent          whether repeated invocations within one request are collapsed to one effect.
 * @param source              provenance label returned to the LLM (which backend produced the data).
 * @param auditCategory       coarse audit grouping used in execution metadata.
 */
public record ToolMetadata(
        String name,
        String description,
        ToolRiskLevel riskLevel,
        ClaimPermission requiredPermission,
        long timeoutMs,
        int maxRetries,
        boolean idempotent,
        String source,
        String auditCategory
) {
    public ToolMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        if (requiredPermission == null) {
            throw new IllegalArgumentException("Tool '" + name + "' must declare a required permission");
        }
    }

    /** Whether this tool reads (false) or writes (true). */
    public boolean isWrite() {
        return riskLevel == ToolRiskLevel.WRITE;
    }
}