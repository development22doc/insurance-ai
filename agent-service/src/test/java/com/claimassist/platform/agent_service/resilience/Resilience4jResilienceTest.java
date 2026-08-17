package com.claimassist.platform.agent_service.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Phase 7 resilience mechanism tests (SCENARIO 3/4/5/11, and unit tests 5/6/7/8/
 * 9/10/11/16/17). These exercise the Resilience4j primitives that back the
 * externalized {@code resilience4j.*} configuration with the same policies
 * (bounded retry of transient failures, no retry of authz/validation/not-found,
 * circuit open/recovery, bulkhead rejection). They are deterministic and do not
 * need a live backend.
 */
class Resilience4jResilienceTest {

    // ----- Retry (7.3, SCENARIO 3/4) ------------------------------------

    @Test
    void retriesTransientFailureThenSucceeds() throws Throwable {
        Retry retry = Retry.of("t", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        AtomicInteger attempts = new AtomicInteger();
        CheckedSupplier<Integer> op = Retry.decorateCheckedSupplier(retry, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("connection reset");
            }
            return 200;
        });
        assertThat(op.get()).isEqualTo(200);
        assertThat(attempts.get()).isEqualTo(3); // 2 transient failures + 1 success
    }

    @Test
    void stopsRetryingAfterBoundedAttempts() throws Throwable {
        Retry retry = Retry.of("t", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        AtomicInteger attempts = new AtomicInteger();
        CheckedSupplier<Integer> op = Retry.decorateCheckedSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new IOException("still down");
        });
        assertThatThrownBy(op::get).isInstanceOf(IOException.class);
        assertThat(attempts.get()).isEqualTo(3); // never infinite
    }

    @Test
    void doesNotRetryAuthorizationFailure() throws Throwable {
        Retry retry = Retry.of("t", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1))
                .retryExceptions(IOException.class)
                .ignoreExceptions(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
                .build());
        AtomicInteger attempts = new AtomicInteger();
        CheckedSupplier<Integer> op = Retry.decorateCheckedSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("denied");
        });
        assertThatThrownBy(op::get)
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        assertThat(attempts.get()).isEqualTo(1); // not retried
    }

    @Test
    void doesNotRetryNotFound() throws Throwable {
        Retry retry = Retry.of("t", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1))
                .retryExceptions(IOException.class)
                .ignoreExceptions(feign.FeignException.NotFound.class)
                .build());
        AtomicInteger attempts = new AtomicInteger();
        CheckedSupplier<Integer> op = Retry.decorateCheckedSupplier(retry, () -> {
            attempts.incrementAndGet();
            throw mock(feign.FeignException.NotFound.class);
        });
        assertThatThrownBy(op::get).isInstanceOf(feign.FeignException.NotFound.class);
        assertThat(attempts.get()).isEqualTo(1); // not retried
    }

    // ----- Circuit breaker (7.5, SCENARIO 5) -----------------------------

    @Test
    void circuitOpensAndFailsFastThenRecovers() {
        // Reflect the externalized policy: min 10 calls, 50% threshold, open 10s, 3 half-open.
        CircuitBreaker cb = CircuitBreaker.of("claimsService", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());

        // Drive the failure rate past the threshold.
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> cb.executeCheckedSupplier(() -> {
                throw new IOException("backend down");
            })).isInstanceOf(IOException.class);
        }

        // Circuit is now open -> requests fail fast without reaching the dependency.
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> cb.executeSupplier(() -> "unreachable"))
                .isInstanceOf(CallNotPermittedException.class);

        // After the open-wait, half-open permits a few probe calls; all succeed -> recovers.
        cb.transitionToHalfOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        for (int i = 0; i < 3; i++) { // permittedNumberOfCallsInHalfOpenState = 3
            assertThat(cb.executeSupplier(() -> "ok")).isEqualTo("ok");
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ----- Bulkhead (7.6, SCENARIO 11) -----------------------------------

    @Test
    void bulkheadRejectsWhenConcurrencyExceeded() {
        Bulkhead bulkhead = Bulkhead.of("claimsService", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ofMillis(1))
                .build());

        bulkhead.acquirePermission(); // consume the single permit
        assertThatThrownBy(bulkhead::acquirePermission)
                .isInstanceOf(BulkheadFullException.class);
        bulkhead.releasePermission();
        bulkhead.acquirePermission(); // freed permit usable again
        bulkhead.releasePermission();
    }

    @Test
    void bulkheadAllowsConcurrencyWithinLimit() {
        Bulkhead bulkhead = Bulkhead.of("claimsService", BulkheadConfig.custom()
                .maxConcurrentCalls(2).maxWaitDuration(Duration.ofMillis(10)).build());
        bulkhead.acquirePermission();
        bulkhead.acquirePermission(); // within limit -> no rejection
        bulkhead.releasePermission();
        bulkhead.releasePermission();
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
    }
}