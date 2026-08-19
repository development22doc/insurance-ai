package com.claimassist.platform.agent_service.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsTest {

    @Test
    void recordsAndReportsCounters() {
        CacheMetrics m = new CacheMetrics();
        m.recordHit();
        m.recordHit();
        m.recordMiss();
        m.recordError();

        assertThat(m.hits()).isEqualTo(2);
        assertThat(m.misses()).isEqualTo(1);
        assertThat(m.errors()).isEqualTo(1);
    }

    @Test
    void resetClearsAllCounters() {
        CacheMetrics m = new CacheMetrics();
        m.recordHit();
        m.recordMiss();
        m.recordError();
        m.reset();

        assertThat(m.hits()).isZero();
        assertThat(m.misses()).isZero();
        assertThat(m.errors()).isZero();
    }

    @Test
    void startsAtZero() {
        CacheMetrics m = new CacheMetrics();
        assertThat(m.hits()).isZero();
        assertThat(m.misses()).isZero();
        assertThat(m.errors()).isZero();
    }
}