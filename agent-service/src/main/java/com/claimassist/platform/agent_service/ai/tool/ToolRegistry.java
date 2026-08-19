package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central catalog of agent tool metadata.
 * <p>
 * The registry is the single place where agent-service's application code
 * looks up operational policy for a tool (risk level, required permission,
 * timeout, retry classification, idempotency, provenance). Spring AI still
 * drives actual tool selection/execution; the registry is consulted by the
 * tools and the {@link ToolExecutionGuard} to enforce that policy.
 * <p>
 * The catalog is immutable after construction (built once from a static
 * definition list), so the registry is thread-safe to share across concurrent
 * requests.
 */
@Component
public class ToolRegistry {

    public static final String CLAIM_STATUS = "get_claim_status";
    public static final String POLICY_COVERAGE = "get_policy_coverage";
    public static final String CLAIM_DOCUMENTS = "get_claim_documents";
    public static final String PROPOSE_CLAIM_UPDATE = "propose_claim_update";

    private static final Map<String, ToolMetadata> METADATA = buildCatalog();

    public ToolRegistry() {
    }

    private static Map<String, ToolMetadata> buildCatalog() {
        Map<String, ToolMetadata> catalog = new LinkedHashMap<>();
        catalog.put(CLAIM_STATUS, new ToolMetadata(
                CLAIM_STATUS,
                "Get the current claim status and its history.",
                ToolRiskLevel.READ, ClaimPermission.VIEW,
                0L, 2, false, "claims-service", "CLAIM_READ"));
        catalog.put(POLICY_COVERAGE, new ToolMetadata(
                POLICY_COVERAGE,
                "Get the claim's underlying policy coverage details.",
                ToolRiskLevel.READ, ClaimPermission.VIEW,
                0L, 2, false, "customer-service", "POLICY_READ"));
        catalog.put(CLAIM_DOCUMENTS, new ToolMetadata(
                CLAIM_DOCUMENTS,
                "Get the documents submitted for this claim.",
                ToolRiskLevel.READ, ClaimPermission.VIEW,
                0L, 2, false, "claims-service", "DOCUMENT_READ"));
        catalog.put(PROPOSE_CLAIM_UPDATE, new ToolMetadata(
                PROPOSE_CLAIM_UPDATE,
                "Propose a claim status change via the business workflow.",
                ToolRiskLevel.WRITE, ClaimPermission.UPDATE_STATUS,
                0L, 0, true, "agent-service", "CLAIM_UPDATE_PROPOSAL"));
        return Collections.unmodifiableMap(catalog);
    }

    /** Look up metadata for a tool name. */
    public Optional<ToolMetadata> metadata(String name) {
        return Optional.ofNullable(METADATA.get(name));
    }

    /** All registered tool metadata (unmodifiable). */
    public Collection<ToolMetadata> all() {
        return METADATA.values();
    }

    /** Risk level for a tool, falling back to READ for unknown names. */
    public ToolRiskLevel riskLevel(String name) {
        ToolMetadata m = METADATA.get(name);
        return m == null ? ToolRiskLevel.READ : m.riskLevel();
    }

    /** Required permission for a tool. */
    public ClaimPermission requiredPermission(String name) {
        return METADATA.get(name).requiredPermission();
    }

    /** Whether a tool is idempotent. */
    public boolean isIdempotent(String name) {
        ToolMetadata m = METADATA.get(name);
        return m != null && m.idempotent();
    }

    /** Per-tool timeout in ms, empty when the global default should be used. */
    public Optional<Long> timeoutMs(String name) {
        ToolMetadata m = METADATA.get(name);
        if (m != null && m.timeoutMs() > 0) {
            return Optional.of(m.timeoutMs());
        }
        return Optional.empty();
    }

    /** Application-level retry budget for a tool (0 = do not retry at this layer). */
    public int maxRetries(String name) {
        ToolMetadata m = METADATA.get(name);
        return m == null ? 0 : m.maxRetries();
    }
}