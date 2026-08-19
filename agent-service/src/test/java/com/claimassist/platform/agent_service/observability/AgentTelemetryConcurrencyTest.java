package com.claimassist.platform.agent_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency / request isolation for telemetry (Phase 6.27, SCENARIO 10).
 * <p>
 * {@link AgentTelemetry} is stateless - all correlation data is passed per
 * call, so concurrent requests must never share or mix telemetry. We run a
 * burst of concurrent full lifecycles with distinct request ids and verify no
 * exceptions and exact aggregate counters, i.e. no dropped/mixed telemetry.
 */
class AgentTelemetryConcurrencyTest {

    @Test
    void concurrentRequestsProduceExactAggregateTelemetryWithoutCrossTalk() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new AgentTelemetry(null, null, registry, null);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int n = i;
                tasks.add(() -> {
                    String requestId = "req-" + n;
                    telemetry.requestStarted(requestId, "corr-" + n, (long) n, (long) n, "model", "ollama");
                    telemetry.llmStarted(requestId, "corr-" + n, (long) n, (long) n, "model", "ollama");
                    telemetry.llmCompleted(requestId, "corr-" + n, (long) n, (long) n, 10, "COMPLETE", 1, null, null);
                    telemetry.toolCompleted(requestId, "corr-" + n, (long) n, (long) n,
                            "get_claim_status", "hash" + n, 5, "SUCCESS");
                    telemetry.responseCompleted(requestId, "corr-" + n, (long) n, (long) n, 20);
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get(); // propagate any telemetry failure
            }
        } finally {
            pool.shutdownNow();
        }

        // Exact aggregate counts prove nothing was dropped or double-counted
        // across threads (telemetry has no shared mutable per-request state).
        assertThat(registry.get("agent.requests.total").counter().count()).isEqualTo(threads);
        assertThat(registry.get("agent.llm.requests").counter().count()).isEqualTo(threads);
        assertThat(registry.get("agent.tool.executions").counter().count()).isEqualTo(threads);
        assertThat(registry.get("agent.request.duration").timer().count()).isEqualTo(threads);
    }
}