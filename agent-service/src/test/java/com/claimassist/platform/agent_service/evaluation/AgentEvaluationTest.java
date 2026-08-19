package com.claimassist.platform.agent_service.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic evaluation framework tests (Phase 6.22 / 6.23 / 6.24).
 * Verifies the dataset loads, that each case can be passed with a correct
 * structural outcome, and that the evaluator fails meaningful (non-fragile)
 * cases rather than comparing exact LLM prose.
 */
class AgentEvaluationTest {

    @Test
    void datasetLoadsWithAllMandatoryCases() {
        List<EvaluationCase> cases = EvaluationDataset.load();
        assertThat(cases).hasSize(6);
        assertThat(cases).extracting(EvaluationCase::id)
                .contains("tool-status", "tool-args-docs", "authz-denied",
                        "guardrail-injection", "context-multiturn", "error-tool-failure");
    }

    @Test
    void datasetContainsNoSecretsOrPii() {
        for (EvaluationCase c : EvaluationDataset.load()) {
            String input = c.input();
            assertThat(input.toLowerCase())
                    .doesNotContain("password").doesNotContain("token=")
                    .doesNotContain("api_key").doesNotContain("jwt").doesNotContain("secret");
        }
    }

    @Test
    void toolSelectionCasePassesWithCorrectTool() {
        EvaluationCase c = EvaluationDataset.byId("tool-status");
        EvaluationResult r = AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_claim_status", false, false, false, false, null));
        assertThat(r.passed()).isTrue();
    }

    @Test
    void toolSelectionCaseFailsWithWrongTool() {
        EvaluationCase c = EvaluationDataset.byId("tool-status");
        EvaluationResult r = AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_policy_coverage", false, false, false, false, null));
        assertThat(r.passed()).isFalse();
        assertThat(r.reasons()).anyMatch(reason -> reason.contains("expected tool=get_claim_status"));
    }

    @Test
    void authorizationCasePassesOnlyWhenDeniedAndNoToolRuns() {
        EvaluationCase c = EvaluationDataset.byId("authz-denied");
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(null, false, true, false, false, null)).passed())
                .isTrue();
        // Running a tool on a denied request is a hard failure.
        EvaluationResult ran = AgentEvaluator.evaluate(c,
                new AgentOutcome("get_claim_status", false, true, false, false, null));
        assertThat(ran.passed()).isFalse();
        assertThat(ran.reasons()).anyMatch(reason -> reason.contains("must not execute a tool"));
    }

    @Test
    void guardrailCaseRejectsAndNeverRunsTool() {
        EvaluationCase c = EvaluationDataset.byId("guardrail-injection");
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(null, false, false, true, false, null)).passed())
                .isTrue();
        EvaluationResult ran = AgentEvaluator.evaluate(c,
                new AgentOutcome("get_claim_status", false, false, true, false, null));
        assertThat(ran.passed()).isFalse();
    }

    @Test
    void contextCaseVerifiesContextUsage() {
        EvaluationCase c = EvaluationDataset.byId("context-multiturn");
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_claim_documents", false, false, false, true, null)).passed()).isTrue();
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_claim_documents", false, false, false, false, null)).passed()).isFalse();
    }

    @Test
    void errorHandlingCaseVerifiesErrorCategory() {
        EvaluationCase c = EvaluationDataset.byId("error-tool-failure");
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_claim_status", false, false, false, false, "TOOL_ERROR")).passed()).isTrue();
        assertThat(AgentEvaluator.evaluate(c, new AgentOutcome(
                "get_claim_status", false, false, false, false, "LLM_ERROR")).passed()).isFalse();
    }
}