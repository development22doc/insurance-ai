package com.claimassist.platform.policy_service.migration;

public interface LegacyPolicySnapshotSource {

    LegacyPolicyDataset extractSnapshot();
}
