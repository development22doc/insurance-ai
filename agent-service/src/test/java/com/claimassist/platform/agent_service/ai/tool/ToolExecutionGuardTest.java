package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutionGuardTest {

    private ToolCallback stub(String name, Runnable body) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
            }

            @Override
            public String call(String input) {
                body.run();
                return "ok";
            }
        };
    }

    private AgentAiProperties props(int maxCalls, long timeoutMs) {
        AgentAiProperties p = new AgentAiProperties();
        p.setMaxToolCalls(maxCalls);
        p.setToolTimeoutMs(timeoutMs);
        p.setAgentTimeoutMs(1000);
        return p;
    }

    @Test
    void truncatesOversizedToolResultExplicitlyWithMarker() {
        AgentAiProperties p = props(10, 5000);
        p.setMaxToolResultChars(20);
        ToolExecutionGuard guard = new ToolExecutionGuard(p, new ToolRegistry());

        String big = "x".repeat(100);
        String result = guard.execute("t", () -> big);

        assertThat(result).hasSizeLessThanOrEqualTo(20 + 80); // truncated to bound + marker
        assertThat(result).startsWith("xxxxxxxxxxxxxxxxxxxx");
        assertThat(result).contains("[truncated:");
        assertThat(result).contains("80 characters omitted");
    }

    @Test
    void doesNotTruncateToolResultWithinBound() {
        AgentAiProperties p = props(10, 5000);
        p.setMaxToolResultChars(1000);
        ToolExecutionGuard guard = new ToolExecutionGuard(p, new ToolRegistry());

        String result = guard.execute("t", () -> "short result");
        assertThat(result).isEqualTo("short result");
        assertThat(result).doesNotContain("[truncated:");
    }

    @Test
    void truncatedToolResultRecordsTelemetry() {
        AgentAiProperties p = props(10, 5000);
        p.setMaxToolResultChars(5);
        com.claimassist.platform.agent_service.support.RecordingAgentTelemetry telemetry =
                new com.claimassist.platform.agent_service.support.RecordingAgentTelemetry();
        ToolExecutionGuard guard = new ToolExecutionGuard(p, new ToolRegistry(),
                "req-t", "corr-t", meta -> { }, telemetry, 99L, 1L, "claimHash");

        guard.execute("get_claim_status", () -> "a-very-long-tool-result");

        assertThat(telemetry.eventTypes()).contains("TOOL_RESULT_TRUNCATED");
        assertThat(telemetry.events().stream()
                .map(com.claimassist.platform.agent_service.support.RecordingAgentTelemetry.Event::requestId)
                .distinct()).containsExactly("req-t");
    }

    @Test
    void nullToolResultIsNotTruncated() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 5000), new ToolRegistry());
        String result = guard.execute("t", () -> null);
        assertThat(result).isNull();
    }

    @Test
    void executesToolWithinBudget() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 5000), new ToolRegistry());
        AtomicInteger calls = new AtomicInteger();
        String result = guard.execute("t", () -> {
            calls.incrementAndGet();
            return "done";
        });
        assertThat(result).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void throwsWhenToolCallLimitExceeded() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(2, 5000), new ToolRegistry());
        assertThat(guard.execute("t", () -> "a")).isEqualTo("a");
        assertThat(guard.execute("t", () -> "b")).isEqualTo("b");
        assertThatThrownBy(() -> guard.execute("t", () -> "c"))
                .isInstanceOf(ToolCallLimitExceededException.class);
    }

    @Test
    void throwsOnToolTimeout() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 100), new ToolRegistry());
        assertThatThrownBy(() -> guard.execute("slow", () -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            return "late";
        })).isInstanceOf(ToolExecutionTimeoutException.class);
    }

    @Test
    void propagatesToolException() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 5000), new ToolRegistry());
        assertThatThrownBy(() -> guard.execute("t", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
    }

    @Test
    void wrapsProviderCallbacks() {
        ToolExecutionGuard guard = new ToolExecutionGuard(props(1, 5000), new ToolRegistry());
        ToolCallback cb = stub("get_claim_status", () -> { });
        ToolCallbackProvider guarded = guard.guarded(ToolCallbackProvider.from(cb));
        assertThat(guarded.getToolCallbacks()).hasSize(1);
        assertThat(guarded.getToolCallbacks()[0].getToolDefinition().name()).isEqualTo("get_claim_status");
        assertThat(guarded.getToolCallbacks()[0].call("{}")).isEqualTo("ok");
    }

    @Test
    void counterIsIsolatedPerGuardInstance() {
        ToolExecutionGuard g1 = new ToolExecutionGuard(props(1, 5000), new ToolRegistry());
        ToolExecutionGuard g2 = new ToolExecutionGuard(props(1, 5000), new ToolRegistry());
        g1.execute("t", () -> "a");
        assertThat(g2.execute("t", () -> "b")).isEqualTo("b"); // g2 unaffected by g1
        assertThatThrownBy(() -> g1.execute("t", () -> "c"))
                .isInstanceOf(ToolCallLimitExceededException.class);
    }

    @Test
    void emitsExecutionMetadataOnSuccess() {
        List<ToolExecutionMetadata> captured = new java.util.ArrayList<>();
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 5000), new ToolRegistry(),
                "req-1", "corr-1", captured::add);
        guard.execute("get_claim_status", () -> "ok");
        assertThat(captured).hasSize(1);
        ToolExecutionMetadata meta = captured.get(0);
        assertThat(meta.requestId()).isEqualTo("req-1");
        assertThat(meta.correlationId()).isEqualTo("corr-1");
        assertThat(meta.toolName()).isEqualTo("get_claim_status");
        assertThat(meta.status()).isEqualTo(ToolExecutionMetadata.STATUS_SUCCESS);
    }

    @Test
    void emitsTimeoutMetadataOnToolTimeout() {
        List<ToolExecutionMetadata> captured = new java.util.ArrayList<>();
        ToolExecutionGuard guard = new ToolExecutionGuard(props(10, 50), new ToolRegistry(),
                "req-2", "corr-2", captured::add);
        assertThatThrownBy(() -> guard.execute("get_claim_status", () -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            return "late";
        })).isInstanceOf(ToolExecutionTimeoutException.class);
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).status()).isEqualTo(ToolExecutionMetadata.STATUS_TIMEOUT);
        assertThat(captured.get(0).toolName()).isEqualTo("get_claim_status");
    }
}
