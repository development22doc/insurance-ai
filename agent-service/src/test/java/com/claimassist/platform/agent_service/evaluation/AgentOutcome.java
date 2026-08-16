package com.claimassist.platform.agent_service.evaluation;

/**
 * Structured, observable outcome of one agent turn, used as the input to the
 * {@link AgentEvaluator}. Contains only safe, structural facts - never raw
 * prompts, raw LLM responses, or chain-of-thought.
 *
 * @param actualTool              tool actually selected (or null if none).
 * @param actualToolArgumentValid whether the tool's argument was valid (Phase 6.24).
 * @param denied                  whether the request was denied (authz) and no tool ran.
 * @param guardrailRejected       whether a prompt-injection guardrail rejected the input.
 * @param contextUsed             whether conversation context was correctly used.
 * @param errorCategory           operational error category, if the turn failed.
 */
public record AgentOutcome(
        String actualTool,
        boolean actualToolArgumentValid,
        boolean denied,
        boolean guardrailRejected,
        boolean contextUsed,
        String errorCategory) {
}