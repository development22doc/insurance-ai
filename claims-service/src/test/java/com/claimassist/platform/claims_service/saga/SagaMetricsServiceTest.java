package com.claimassist.platform.claims_service.saga;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SagaMetricsServiceTest {

    private MeterRegistry registry;

    private SagaMetricsService metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SagaMetricsService(registry);
    }

    @Test
    void recordsLifecycleCounters() {
        metrics.incrementStarted();
        metrics.incrementStarted();
        metrics.incrementCompleted();
        metrics.incrementFailed();
        metrics.incrementTimedOut();
        metrics.incrementCompensated();
        metrics.incrementRecoveryRetry();

        assertThat(counter("claim.saga.started")).isEqualTo(2);
        assertThat(counter("claim.saga.completed")).isEqualTo(1);
        assertThat(counter("claim.saga.failed")).isEqualTo(1);
        assertThat(counter("claim.saga.timed_out")).isEqualTo(1);
        assertThat(counter("claim.saga.compensated")).isEqualTo(1);
        assertThat(counter("claim.saga.recovery_retry")).isEqualTo(1);
    }

    @Test
    void recordsStepLatencyTimer() {
        metrics.recordStepLatency("PAYMENT", Duration.ofMillis(150));
        metrics.recordStepLatency("PAYMENT", Duration.ofMillis(250));

        double count = registry.find("claim.saga.step.duration")
                .tag("step", "PAYMENT").timer().count();
        assertThat(count).isEqualTo(2);
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }
}