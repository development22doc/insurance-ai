package com.claimassist.platform.policy_service.migration;

public class StaticLegacyPolicySnapshotSource implements LegacyPolicySnapshotSource {

    private final LegacyPolicyDataset dataset;

    public StaticLegacyPolicySnapshotSource(LegacyPolicyDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public LegacyPolicyDataset extractSnapshot() {
        return dataset;
    }
}
