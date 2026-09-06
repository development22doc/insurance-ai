package com.claimassist.platform.policy_service.migration;

import java.util.ArrayList;
import java.util.List;

public class StaticLegacyPolicySource implements LegacyPolicySource {

    private final List<LegacyPolicyRecord> records;

    public StaticLegacyPolicySource(List<LegacyPolicyRecord> records) {
        this.records = records == null ? new ArrayList<>() : new ArrayList<>(records);
    }

    @Override
    public List<LegacyPolicyRecord> findLegacyPolicies() {
        return List.copyOf(records);
    }
}
