package com.claimassist.platform.agent_service.ai.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, registry-driven planner that completes the set of READ tool
 * calls a request needs when an unreliable local model (qwen2.5-coder:3b)
 * stops after emitting none, or only some, of the tools a multi-part question
 * requires.
 * <p>
 * The planner is <em>only</em> a completion helper:
 * <ul>
 *   <li>It computes an ordered, read-only plan from the user's own request text
 *       matched against per-tool relevance terms, so the same question always
 *       yields the same plan (no model randomness).</li>
 *   <li>It only ever proposes tools that are registered in {@link ToolRegistry}
 *       AND present in the request's guarded callback set (so nothing outside
 *       the {@link ToolExecutionGuard} boundary is ever invoked).</li>
 *   <li>It never plans the WRITE tool {@code propose_claim_update}: writes are
 *       never auto-issued here, they must be selected by the model itself. Only
 *       READ tools may be planner-completed.</li>
 *   <li>Execution of any planned tool still flows through the existing
 *       {@link QwenToolCallingManager} guarded-callback path; the planner only
 *       decides <em>which</em> READ tool to complete next.</li>
 * </ul>
 * <p>
 * The model-first behaviour is preserved: if the model itself emits the needed
 * tool calls, the plan is already satisfied and nothing is forced.
 */
public final class ToolPlanner {

    private ToolPlanner() {
    }

    /**
     * Compute the ordered set of READ tools implied by {@code requestText}, limited
     * to tools present in {@code registeredNames}.
     *
     * @return immutable, ordered plan; empty when nothing is implied or nothing is registered.
     */
    public static List<String> plan(String requestText, Set<String> registeredNames) {
        if (requestText == null || requestText.isBlank()) {
            return List.of();
        }
        String text = requestText.toLowerCase(Locale.ROOT);
        List<String> plan = new ArrayList<>(3);
        if (registeredNames != null && registeredNames.contains(ToolRegistry.CLAIM_STATUS)
                && matchesAny(text, "status", "state", "progress", "where is my claim", "situation")) {
            plan.add(ToolRegistry.CLAIM_STATUS);
        }
        if (registeredNames != null && registeredNames.contains(ToolRegistry.POLICY_COVERAGE)
                && matchesAny(text, "coverage", "policy", "covered", "deductible", "limit", "premium")) {
            plan.add(ToolRegistry.POLICY_COVERAGE);
        }
        if (registeredNames != null && registeredNames.contains(ToolRegistry.CLAIM_DOCUMENTS)
                && matchesAny(text, "document", "documents", "submitted", "submit", "upload", "attached", "report", "paper")) {
            plan.add(ToolRegistry.CLAIM_DOCUMENTS);
        }
        // propose_claim_update (the WRITE tool) is intentionally NEVER planned here.
        return Collections.unmodifiableList(plan);
    }

    private static boolean matchesAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
