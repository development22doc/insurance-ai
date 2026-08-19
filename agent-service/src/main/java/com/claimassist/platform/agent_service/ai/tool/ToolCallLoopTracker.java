package com.claimassist.platform.agent_service.ai.tool;

import java.util.Set;

/**
 * Request-scoped detector for pathological Qwen text tool-calling loops.
 * <p>
 * qwen2.5-coder can occasionally fail to break out of tool-calling mode and keep
 * re-emitting tool calls instead of producing a final text answer. Two shapes of
 * loop are recognised:
 * <ul>
 *   <li><b>Consecutive identical</b> - the model emits exactly the same tool-call
 *       object back to back (same signature).</li>
 *   <li><b>Alternating re-invocation</b> - an already-completed tool is re-invoked.
 *       Because the signature usually changes (the model alternates among the tools
 *       it already completed), a consecutive-identical check alone never fires. The
 *       tracker instead counts re-invocations of tools already executed this turn.</li>
 * </ul>
 * <p>
 * A single accidental repeat is tolerated (bounded threshold); only an actual loop
 * is terminated. The instance is created once per request and threaded through every
 * streamed turn, so no state leaks between requests or concurrent conversations.
 */
public final class ToolCallLoopTracker {

    /** Back-to-back identical tool-call signature repeats before aborting. */
    public static final int CONSECUTIVE_IDENTICAL_LIMIT = 3;

    /** Number of tolerated re-invocations of an already-completed tool before aborting. */
    public static final int SAME_TOOL_REINVOCATION_LIMIT = 3;

    /** Decision for a newly emitted model tool call. */
    public enum Verdict {
        /** Nothing anomalous: the call may be executed. */
        OK,
        /** Re-invocation of an already-completed tool, within tolerance: may still execute. */
        REPEAT_TOLERATED,
        /** A loop is detected: do NOT execute; return the controlled termination response. */
        TERMINATE
    }

    private String lastCallSignature;
    private int consecutiveIdentical;
    private int sameToolReinvocations;

    /**
     * Register a newly emitted model tool call and return the loop verdict.
     *
     * @param renderedSignature the exact accumulated tool-call text; used only for the
     *                          consecutive-identical signal.
     * @param toolName          the parsed tool name, or {@code null} if indeterminate.
     * @param executedThisTurn  the set of tools already completed this turn. For a first
     *                          invocation {@code toolName} must NOT be present; for a
     *                          re-invocation it IS present.
     */
    public Verdict register(String renderedSignature, String toolName, Set<String> executedThisTurn) {
        if (lastCallSignature != null && lastCallSignature.equals(renderedSignature)) {
            consecutiveIdentical++;
        }
        else {
            consecutiveIdentical = 1;
        }
        lastCallSignature = renderedSignature;
        if (consecutiveIdentical >= CONSECUTIVE_IDENTICAL_LIMIT) {
            return Verdict.TERMINATE;
        }

        if (toolName != null && executedThisTurn != null && executedThisTurn.contains(toolName)) {
            sameToolReinvocations++;
            if (sameToolReinvocations >= SAME_TOOL_REINVOCATION_LIMIT) {
                return Verdict.TERMINATE;
            }
            return Verdict.REPEAT_TOLERATED;
        }
        return Verdict.OK;
    }

    /** Number of consecutive identical tool-call signatures seen (diagnostics/tests). */
    public int consecutiveIdentical() {
        return consecutiveIdentical;
    }

    /** Number of re-invocations of already-completed tools seen (diagnostics/tests). */
    public int sameToolReinvocations() {
        return sameToolReinvocations;
    }
}