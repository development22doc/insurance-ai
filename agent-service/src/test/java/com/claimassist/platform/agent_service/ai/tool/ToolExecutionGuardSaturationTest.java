package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.support.RecordingAgentTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Phase 7 bounded tool-executor hardening: when the shared tool
 * pool AND its finite work queue are saturated (an overload burst), a new tool
 * invocation fails FAST as a controlled transient failure instead of being
 * queued without bound. Uses a tiny, pre-saturated executor to make the
 * rejection path deterministic (no dependence on the shared static pool).
 */
class ToolExecutionGuardSaturationTest {

    /** Single-thread pool with a ONE-slot queue → submits reject once worker AND queue are full. */
    private ExecutorService saturatedExecutor;

    @AfterEach
    void tearDown() {
        if (saturatedExecutor != null) {
            saturatedExecutor.shutdownNow();
        }
    }

    /** Fill the single worker and the single queued slot so the next submit must be rejected. */
    private CountDownLatch saturate() throws InterruptedException {
        saturatedExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                r -> { Thread t = new Thread(r, "sat-tool-exec"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Task A occupies the single worker...
        saturatedExecutor.submit(() -> {
            running.countDown();
            awaitQuietly(release);
            return null;
        });
        assertThat(running.await(5, TimeUnit.SECONDS)).as("worker started").isTrue();
        // Task B is placed in the one queued slot (synchronous enqueue) - pool and queue now full.
        saturatedExecutor.submit(() -> {
            awaitQuietly(release);
            return null;
        });
        return release;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void rejectsFastWhenToolPoolAndQueueAreSaturated() throws InterruptedException {
        CountDownLatch release = saturate();

        AgentAiProperties props = new AgentAiProperties();
        props.setMaxToolCalls(10);
        props.setToolTimeoutMs(5000);
        ToolExecutionGuard guard = new ToolExecutionGuard(props, new ToolRegistry(),
                "req-sat", "corr-sat", meta -> { }, null, 99L, 1L, "hash", saturatedExecutor);

        assertThatThrownBy(() -> guard.execute("get_claim_status", () -> "ok"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("get_claim_status");

        release.countDown();
    }

    @Test
    void recordsFailedMetadataAndTelemetryOnRejection() throws InterruptedException {
        CountDownLatch release = saturate();

        RecordingAgentTelemetry telemetry = new RecordingAgentTelemetry();
        List<ToolExecutionMetadata> captured = new java.util.ArrayList<>();
        AgentAiProperties props = new AgentAiProperties();
        props.setMaxToolCalls(10);
        props.setToolTimeoutMs(5000);
        ToolExecutionGuard guard = new ToolExecutionGuard(props, new ToolRegistry(),
                "req-sat-2", "corr-sat-2", captured::add, telemetry, 7L, 2L, "h", saturatedExecutor);

        assertThatThrownBy(() -> guard.execute("propose_claim_update", () -> "ok"))
                .isInstanceOf(ToolExecutionException.class);

        assertThat(captured).hasSize(1);
        ToolExecutionMetadata meta = captured.getFirst();
        assertThat(meta.status()).isEqualTo(ToolExecutionMetadata.STATUS_FAILED);
        assertThat(meta.toolName()).isEqualTo("propose_claim_update");
        assertThat(meta.requestId()).isEqualTo("req-sat-2");
        assertThat(meta.correlationId()).isEqualTo("corr-sat-2");
        assertThat(telemetry.eventTypes()).contains("TOOL_FAILED");

        release.countDown();
    }
}