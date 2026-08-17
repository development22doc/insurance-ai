package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.support.RecordingAgentTelemetry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tool telemetry through the guard (SCENARIO 2 tool failure, SCENARIO 7
 * multi-tool, Phase 6.4/6.5). Every tool execution produces structured events
 * with safe metadata (hashed resource reference, tool name, result) plus
 * low-cardinality metrics.
 */
class ToolExecutionGuardObservabilityTest {

    private final RecordingAgentTelemetry telemetry = new RecordingAgentTelemetry();

    private ToolExecutionGuard guard(int maxCalls, long timeoutMs) {
        AgentAiProperties props = new AgentAiProperties();
        props.setMaxToolCalls(maxCalls);
        props.setToolTimeoutMs(timeoutMs);
        return new ToolExecutionGuard(props, new ToolRegistry(), "req-1", "corr-1",
                meta -> { }, telemetry, 99L, 42L, "claimHash");
    }

    @Test
    void successfulToolEmitsRequestedStartedCompletedWithSafeMetadata() {
        guard(10, 5000).execute("get_claim_status", () -> "{\"status\":\"ok\"}");

        assertThat(telemetry.eventTypes())
                .containsSubsequence("TOOL_REQUESTED", "TOOL_STARTED", "TOOL_COMPLETED");
        assertThat(telemetry.eventTypes()).doesNotContain("TOOL_FAILED");
        // All tool events are correlated to the same request id.
        assertThat(telemetry.events().stream().map(RecordingAgentTelemetry.Event::requestId).distinct())
                .containsExactly("req-1");
    }

    @Test
    void failingToolEmitsToolFailedAndThrows() {
        ToolExecutionGuard g = guard(10, 5000);
        assertThatThrownBy(() -> g.execute("get_claim_status",
                () -> { throw new IllegalStateException("backend down"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(telemetry.eventTypes())
                .containsSubsequence("TOOL_REQUESTED", "TOOL_STARTED", "TOOL_FAILED");
        assertThat(telemetry.eventTypes()).doesNotContain("TOOL_COMPLETED");
    }

    @Test
    void multiToolChainIsTraceableInOrder() {
        ToolExecutionGuard g = guard(10, 5000);
        g.execute("get_claim_status", () -> "ok");
        g.execute("get_policy_coverage", () -> "ok");

        assertThat(telemetry.eventTypes())
                .containsSubsequence("TOOL_REQUESTED", "TOOL_COMPLETED",
                        "TOOL_REQUESTED", "TOOL_COMPLETED");
    }

    @Test
    void toolLimitExceededEmitsToolFailed() {
        ToolExecutionGuard g = guard(1, 5000);
        g.execute("get_claim_status", () -> "ok");
        assertThatThrownBy(() -> g.execute("get_policy_coverage", () -> "ok"))
                .isInstanceOf(ToolCallLimitExceededException.class);
        assertThat(telemetry.eventTypes()).contains("TOOL_FAILED");
    }
}