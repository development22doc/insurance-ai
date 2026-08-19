package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallLoopTrackerTest {

    @Test
    void permitsFirstCalls() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        assertThat(tracker.register("sig1", "get_claim_status", Set.of())).isEqualTo(ToolCallLoopTracker.Verdict.OK);
    }

    @Test
    void toleratesReinvocationWithinLimit() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        Set<String> executed = Set.of("get_claim_status");
        assertThat(tracker.register("s1", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
        assertThat(tracker.register("s2", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.REPEAT_TOLERATED);
    }

    @Test
    void terminatesOnRepeatedReinvocation() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        Set<String> executed = Set.of("get_claim_status");
        tracker.register("s1", "get_claim_status", executed);
        tracker.register("s2", "get_claim_status", executed);
        assertThat(tracker.register("s3", "get_claim_status", executed))
                .isEqualTo(ToolCallLoopTracker.Verdict.TERMINATE);
    }

    @Test
    void terminatesOnConsecutiveIdenticalSignatures() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        tracker.register("same", "tool_a", Set.of());
        tracker.register("same", "tool_a", Set.of());
        assertThat(tracker.register("same", "tool_a", Set.of()))
                .isEqualTo(ToolCallLoopTracker.Verdict.TERMINATE);
    }

    @Test
    void newSignatureResetsConsecutiveCounter() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        tracker.register("s1", "tool_a", Set.of());
        tracker.register("s1", "tool_a", Set.of());
        assertThat(tracker.consecutiveIdentical()).isEqualTo(2);

        tracker.register("different", "tool_b", Set.of());
        assertThat(tracker.consecutiveIdentical()).isEqualTo(1);
    }

    @Test
    void countersAreExposed() {
        ToolCallLoopTracker tracker = new ToolCallLoopTracker();
        tracker.register("s1", "get_claim_status", Set.of("get_claim_status"));
        assertThat(tracker.consecutiveIdentical()).isEqualTo(1);
        assertThat(tracker.sameToolReinvocations()).isEqualTo(1);
    }
}