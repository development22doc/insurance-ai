package com.claimassist.platform.agent_service.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, deterministic evaluation framework (Phase 6.22 / 6.24).
 * <p>
 * Evaluates an observable {@link AgentOutcome} against an {@link EvaluationCase}
 * using STRUCTURAL assertions (tool selected, tool argument validity,
 * authorization result, guardrail result, context usage, error category). It
 * deliberately never compares variable LLM prose with exact strings, so the
 * checks are meaningful and stable.
 * <p>
 * This is intentionally NOT a large evaluation platform - it is a testable
 * foundation for asserting agent behaviour in unit and (real-Ollama)
 * integration tests.
 */
public final class AgentEvaluator {

    private AgentEvaluator() {}

    /** Evaluate an outcome against a case; returns pass/fail plus specific reasons. */
    public static EvaluationResult evaluate(EvaluationCase c, AgentOutcome o) {
        List<String> reasons = new ArrayList<>();
        switch (c.category()) {
            case TOOL_SELECTION:
                if (!equal(c.expectedTool(), o.actualTool())) {
                    reasons.add("expected tool=" + c.expectedTool() + " but got=" + o.actualTool());
                }
                break;
            case TOOL_ARGUMENTS:
                if (!o.actualToolArgumentValid()) {
                    reasons.add("expected valid tool arguments but outcome reports invalid");
                }
                if (c.expectedTool() != null && !equal(c.expectedTool(), o.actualTool())) {
                    reasons.add("expected tool=" + c.expectedTool() + " but got=" + o.actualTool());
                }
                break;
            case AUTHORIZATION:
                if (o.denied() != c.expectedDenied()) {
                    reasons.add("expected denied=" + c.expectedDenied() + " but got=" + o.denied());
                }
                if (c.expectedDenied() && o.actualTool() != null) {
                    reasons.add("denied request must not execute a tool, but " + o.actualTool() + " ran");
                }
                break;
            case GUARDRAIL:
                if (o.guardrailRejected() != c.expectedGuardrailRejected()) {
                    reasons.add("expected guardrailRejected=" + c.expectedGuardrailRejected()
                            + " but got=" + o.guardrailRejected());
                }
                if (c.expectedGuardrailRejected() && o.actualTool() != null) {
                    reasons.add("guardrail-rejected input must not execute any tool");
                }
                break;
            case CONTEXT:
                if (o.contextUsed() != c.expectedContextUsed()) {
                    reasons.add("expected contextUsed=" + c.expectedContextUsed() + " but got=" + o.contextUsed());
                }
                break;
            case ERROR_HANDLING:
                if (!equal(c.expectedErrorCategory(), o.errorCategory())) {
                    reasons.add("expected errorCategory=" + c.expectedErrorCategory()
                            + " but got=" + o.errorCategory());
                }
                break;
            case BUSINESS_OUTCOME:
            case REGRESSION:
            default:
                // Regression/business cases assert overall success unless the case
                // explicitly expects a denial or guardrail rejection.
                boolean expectFail = c.expectedDenied() || c.expectedGuardrailRejected();
                if (!expectFail && (o.denied() || o.guardrailRejected())) {
                    reasons.add("expected a normal outcome but request was denied/rejected");
                }
                break;
        }
        return reasons.isEmpty() ? EvaluationResult.pass(c) : EvaluationResult.fail(c, reasons);
    }

    private static boolean equal(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && a.equals(b);
    }
}