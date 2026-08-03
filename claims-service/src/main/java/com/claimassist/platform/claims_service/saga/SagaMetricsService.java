package com.claimassist.platform.claims_service.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SagaMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> stepTimers = new ConcurrentHashMap<>();

    public SagaMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementStarted() {
        counter("claim.saga.started").increment();
    }

    public void incrementCompleted() {
        counter("claim.saga.completed").increment();
    }

    public void incrementFailed() {
        counter("claim.saga.failed").increment();
    }

    public void incrementTimedOut() {
        counter("claim.saga.timed_out").increment();
    }

    public void incrementCompensated() {
        counter("claim.saga.compensated").increment();
    }

    public void incrementRecoveryRetry() {
        counter("claim.saga.recovery_retry").increment();
    }

    public void recordStepLatency(String step, Duration duration) {
        Timer timer = stepTimers.computeIfAbsent(step,
                s -> Timer.builder("claim.saga.step.duration")
                        .tag("step", s)
                        .register(meterRegistry));
        timer.record(duration);
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }
}

