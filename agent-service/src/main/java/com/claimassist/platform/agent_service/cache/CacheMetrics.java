package com.claimassist.platform.agent_service.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal, diagnosable cache counters (Phase 5.15 boundary). These are the ONLY
 * observability added - hit/miss/error tallies so cache behaviour can be
 * reasoned about and tested. Sensitive cache contents are never logged or
 * captured. All counters are thread-safe {@link AtomicLong}s.
 */
public class CacheMetrics {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long errors() {
        return errors.get();
    }

    public void reset() {
        hits.set(0);
        misses.set(0);
        errors.set(0);
    }
}