package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceLoggingPropertiesTest {

    @Test
    void defaults_areSet() {
        PerformanceLoggingProperties p = new PerformanceLoggingProperties();
        assertThat(p.getInfoThresholdMs()).isEqualTo(200L);
        assertThat(p.getWarnThresholdMs()).isEqualTo(1000L);
        assertThat(p.getErrorThresholdMs()).isEqualTo(5000L);
        assertThat(p.getCategories()).isEmpty();
    }

    @Test
    void settersAndGetters_roundTrip() {
        PerformanceLoggingProperties p = new PerformanceLoggingProperties();
        p.setInfoThresholdMs(50L);
        p.setWarnThresholdMs(100L);
        p.setErrorThresholdMs(200L);
        assertThat(p.getInfoThresholdMs()).isEqualTo(50L);
        assertThat(p.getWarnThresholdMs()).isEqualTo(100L);
        assertThat(p.getErrorThresholdMs()).isEqualTo(200L);
    }

    @Test
    void nestedThresholds_gettersAndSetters() {
        PerformanceLoggingProperties.Thresholds t = new PerformanceLoggingProperties.Thresholds();
        assertThat(t.getInfo()).isEqualTo(200L);
        assertThat(t.getWarn()).isEqualTo(1000L);
        assertThat(t.getError()).isEqualTo(5000L);

        t.setInfo(10L);
        t.setWarn(20L);
        t.setError(30L);
        assertThat(t.getInfo()).isEqualTo(10L);
        assertThat(t.getWarn()).isEqualTo(20L);
        assertThat(t.getError()).isEqualTo(30L);
    }

    @Test
    void categories_roundTrip() {
        PerformanceLoggingProperties p = new PerformanceLoggingProperties();
        PerformanceLoggingProperties.Thresholds t = new PerformanceLoggingProperties.Thresholds();
        java.util.Map<String, PerformanceLoggingProperties.Thresholds> cats = new java.util.HashMap<>();
        cats.put("DATABASE", t);
        p.setCategories(cats);
        assertThat(p.getCategories().get("DATABASE")).isSameAs(t);
    }
}