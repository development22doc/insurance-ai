package com.claimassist.platform.agent_service.evaluation;

/**
 * Category of behaviour an evaluation case exercises (Phase 6.22). Keeping
 * evaluation structural (tool selected, authz outcome, guardrail outcome,
 * context usage) rather than comparing variable LLM prose makes the checks
 * deterministic and meaningful.
 */
public enum EvaluationCategory {
    TOOL_SELECTION,
    TOOL_ARGUMENTS,
    AUTHORIZATION,
    GUARDRAIL,
    CONTEXT,
    ERROR_HANDLING,
    BUSINESS_OUTCOME,
    REGRESSION
}