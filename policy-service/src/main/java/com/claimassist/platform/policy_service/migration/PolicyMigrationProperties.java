package com.claimassist.platform.policy_service.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "policy.migration")
public class PolicyMigrationProperties {

    private Map<Long, Long> legacyCoveragePlanMap = new HashMap<>();

    public Map<Long, Long> getLegacyCoveragePlanMap() {
        return legacyCoveragePlanMap;
    }

    public void setLegacyCoveragePlanMap(Map<Long, Long> legacyCoveragePlanMap) {
        this.legacyCoveragePlanMap = legacyCoveragePlanMap == null ? new HashMap<>() : new HashMap<>(legacyCoveragePlanMap);
    }
}
