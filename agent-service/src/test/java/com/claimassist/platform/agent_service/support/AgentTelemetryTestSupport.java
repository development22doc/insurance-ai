package com.claimassist.platform.agent_service.support;

import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Shared helper for tests that construct {@link AgentTelemetry} without a full
 * Spring context. Returns a fail-safe instance bound to a
 * {@link SimpleMeterRegistry} so metrics can be asserted and telemetry never
 * breaks the flow under test.
 */
public final class AgentTelemetryTestSupport {

    private AgentTelemetryTestSupport() {}

    public static AgentTelemetry telemetry() {
        return new AgentTelemetry(null, null, new SimpleMeterRegistry(), null);
    }
}