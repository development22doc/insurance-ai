package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic regression coverage for {@link ToolCallLoopTracker}, the
 * request-scoped guard that aborts a pathological Qwen text tool-calling loop
 * before it can exhaust the {@code maxToolCalls} budget.
 * <p>
 * The guard must catch BOTH a back-to-back identical call and the harder
 * "alternating re-invocation" loop, where the model re-invokes tools it has
 * already completed this turn (e.g. documents -&gt; status -&gt; propose -&gt;
 * status -&gt; documents -&gt; propose -&gt; ...) even though no two calls are
 * identical in a row.
 */
class ToolCallLoopTrackerTest {

    private static final int MAX_TOOL_CALLS = 10;

    private ToolCallLoopTracker tracker() {
        return new ToolCallLoopTracker();
    }

    @Test
    void firstInvocationOfEachToolExecutesNormally() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        assertThat(tracker.register("{\"name\":\"get_claim_documents\",\"arguments\":{}}",
                "get_claim_documents", executed)).isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("get_claim_documents");

        assertThat(tracker.register("{\"name\":\"get_claim_status\",\"arguments\":{}}",
                "get_claim_status", executed)).isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("get_claim_status");

        assertThat(tracker.register("{\"name\":\"propose_claim_update\",\"arguments\":{}}",
                "propose_claim_update", executed)).isEqualTo(ToolCallLoopTracker.Verdict.OK);

        assertThat(tracker.sameToolReinvocations()).isZero();
    }

    @Test
    void alternatingReinvocationLoopTerminatesBeforeMaxToolCalls() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        String[] order = {"get_claim_documents", "get_claim_status", "propose_claim_update"};

        // First cycle: every tool invoked for the first time is legitimate.
        int call = 0;
        for (int i = 0; i < 3; i++) {
            ToolCallLoopTracker.Verdict v = tracker.register(rendered(order[i], call),
                    order[i], executed);
            assertThat(v).isEqualTo(ToolCallLoopTracker.Verdict.OK);
            executed.add(order[i]);
            call++;
        }
        assertThat(tracker.sameToolReinvocations()).isZero();

        // Alternating re-invocation loop: status, documents, propose, status, ...
        boolean terminated = false;
        while (!terminated && call < MAX_TOOL_CALLS) {
            String tool = order[call % 3];
            ToolCallLoopTracker.Verdict v = tracker.register(rendered(tool, call), tool, executed);
            call++;
            if (v == ToolCallLoopTracker.Verdict.TERMINATE) {
                terminated = true;
            }
            else {
                // Detection occurs: the re-invocations are recognised as repeats.
                assertThat(v).isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
                assertThat(tracker.sameToolReinvocations()).isPositive();
            }
        }

        assertThat(terminated).as("alternating loop must be terminated before the hard budget").isTrue();
        assertThat(call).isLessThan(MAX_TOOL_CALLS);
        // Two accidental repeats are tolerated; the third re-invocation terminates.
        assertThat(call).isEqualTo(6);
        assertThat(tracker.sameToolReinvocations()).isEqualTo(ToolCallLoopTracker.SAME_TOOL_REINVOCATION_LIMIT);
    }

    @Test
    void alternatingPairLoopTerminatesBeforeBudget() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        // A -> B (both new) then A -> B -> A -> B ...
        assertThat(tracker.register("sigA1", "A", executed)).isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("A");
        assertThat(tracker.register("sigB1", "B", executed)).isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("B");

        int call = 2;
        boolean terminated = false;
        String[] pair = {"A", "B"};
        while (!terminated && call < MAX_TOOL_CALLS) {
            String tool = pair[call % 2];
            tracker.register("sig" + tool + call, tool, executed);
            call++;
            terminated = tracker.sameToolReinvocations() >= ToolCallLoopTracker.SAME_TOOL_REINVOCATION_LIMIT;
        }
        assertThat(terminated).isTrue();
        assertThat(call).isLessThan(MAX_TOOL_CALLS);
    }

    @Test
    void singleAccidentalRepeatIsTolerated() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        assertThat(tracker.register("sigA1", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("get_claim_status");

        // One accidental re-read is tolerated, not treated as a loop.
        assertThat(tracker.register("sigA2", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
        assertThat(tracker.sameToolReinvocations()).isEqualTo(1);
    }

    @Test
    void consecutiveIdenticalCallsStillTerminate() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        // First two identical calls tolerated, third identical aborts.
        assertThat(tracker.register("{\"name\":\"get_claim_status\"}", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("get_claim_status");
        assertThat(tracker.register("{\"name\":\"get_claim_status\"}", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
        assertThat(tracker.register("{\"name\":\"get_claim_status\"}", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.TERMINATE);
        assertThat(tracker.consecutiveIdentical()).isEqualTo(ToolCallLoopTracker.CONSECUTIVE_IDENTICAL_LIMIT);
    }

    @Test
    void consecutiveIdenticalAndReinvocationGuardAreIndependent() {
        // An alternating loop where signatures differ must fire the re-invocation
        // guard even though the consecutive-identical signal never accumulates.
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();
        executed.add("get_claim_status");
        executed.add("get_claim_documents");
        executed.add("propose_claim_update");

        // Different signatures, all re-invocations of already-completed tools.
        assertThat(tracker.register("status#1", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
        assertThat(tracker.register("documents#1", "get_claim_documents", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
        assertThat(tracker.sameToolReinvocations()).isEqualTo(2);
        assertThat(tracker.consecutiveIdentical()).isEqualTo(1); // never accumulated

        assertThat(tracker.register("propose#1", "propose_claim_update", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.TERMINATE);
    }

    @Test
    void legitimateStatusThenProposeStillWorks() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        assertThat(tracker.register("{\"name\":\"get_claim_status\"}", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.OK);
        executed.add("get_claim_status");

        // propose_claim_update is a NEW tool (not yet executed): legitimate, executes.
        assertThat(tracker.register("{\"name\":\"propose_claim_update\"}", "propose_claim_update", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.OK);

        assertThat(tracker.sameToolReinvocations()).isZero();
        assertThat(tracker.consecutiveIdentical()).isEqualTo(1);
    }

    @Test
    void legitimateStatusDocumentsThenProposeStillWorks() {
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();

        String[] sequence = {"get_claim_status", "get_claim_documents", "propose_claim_update"};
        for (String tool : sequence) {
            assertThat(tracker.register(rendered(tool, 1), tool, executed))
                    .isEqualTo(ToolCallLoopTracker.Verdict.OK);
            executed.add(tool);
        }
        assertThat(tracker.sameToolReinvocations()).isZero();
    }

    @Test
    void newToolAfterRepeatsIsStillProgress() {
        // Once a different, not-yet-executed tool is requested it is treated as
        // progress and a fresh sequence is permitted (reset of the re-invocation
        // guard is NOT required for legitimate distinct calls).
        ToolCallLoopTracker tracker = tracker();
        Set<String> executed = new LinkedHashSet<>();
        executed.add("get_claim_status");

        assertThat(tracker.register("status#1", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);

        // A genuinely new tool (coverage) is not a re-invocation.
        assertThat(tracker.register("coverage#1", "get_policy_coverage", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.OK);
    }

    private static String rendered(String tool, int n) {
        return "{\"name\":\"" + tool + "\",\"arguments\":{\"round\":" + n + "}}";
    }
}