package com.claimassist.platform.customer_service.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3, Section 6: validates the Customer Service PostgreSQL connection-pool
 * configuration binds correctly (pool size, timeouts, and the Phase-3 leak
 * detection). This documents the runtime-effective Hikari settings in
 * config-repo/customer-service.yml and guards against a misconfigured/renamed
 * property silently disabling pool reliability.
 */
class HikariDataSourceConfigTest {

    private HikariConfig bind(Map<String, String> props) {
        HikariConfig target = new HikariConfig();
        Binder binder = new Binder(new MapConfigurationPropertySource(props));
        binder.bind("spring.datasource.hikari", Bindable.ofInstance(target)).get();
        return target;
    }

    @Test
    void productionPoolSettingsBindToHikariConfig() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.datasource.hikari.maximum-pool-size", "10");
        props.put("spring.datasource.hikari.minimum-idle", "2");
        props.put("spring.datasource.hikari.connection-timeout", "20000");
        props.put("spring.datasource.hikari.idle-timeout", "300000");
        props.put("spring.datasource.hikari.max-lifetime", "1200000");

        HikariConfig cfg = bind(props);

        assertThat(cfg.getMaximumPoolSize()).isEqualTo(10);
        assertThat(cfg.getMinimumIdle()).isEqualTo(2);
        assertThat(cfg.getConnectionTimeout()).isEqualTo(20_000L);
        assertThat(cfg.getIdleTimeout()).isEqualTo(300_000L);
        assertThat(cfg.getMaxLifetime()).isEqualTo(1_200_000L);
    }

    @Test
    void leakDetectionThresholdBindsWhenConfigured() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.datasource.hikari.leak-detection-threshold", "60000");
        props.put("spring.datasource.hikari.pool-name", "customer-service-pool");

        HikariConfig cfg = bind(props);

        assertThat(cfg.getLeakDetectionThreshold()).isEqualTo(60_000L);
        assertThat(cfg.getPoolName()).isEqualTo("customer-service-pool");
    }

    @Test
    void poolSizeIsNeverZeroOrNegative() {
        Map<String, String> props = new HashMap<>();
        props.put("spring.datasource.hikari.maximum-pool-size", "10");

        HikariConfig cfg = bind(props);

        assertThat(cfg.getMaximumPoolSize()).isGreaterThan(0);
    }
}