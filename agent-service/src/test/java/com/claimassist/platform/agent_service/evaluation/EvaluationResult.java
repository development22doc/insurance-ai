package com.claimassist.platform.agent_service.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of evaluating one {@link EvaluationCase} against an
 * {@link AgentOutcome}. Holds the case id, whether it passed, and the list of
 * specific reasons it failed (empty when passed) so failures are actionable.
 */
public record EvaluationResult(EvaluationCase evaluationCase, boolean passed, List<String> reasons) {

    public static EvaluationResult pass(EvaluationCase c) {
        return new EvaluationResult(c, true, List.of());
    }

    public static EvaluationResult fail(EvaluationCase c, List<String> reasons) {
        return new EvaluationResult(c, false, reasons);
    }
}