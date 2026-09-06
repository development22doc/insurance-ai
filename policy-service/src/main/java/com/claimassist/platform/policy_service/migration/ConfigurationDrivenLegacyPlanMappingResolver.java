package com.claimassist.platform.policy_service.migration;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConfigurationDrivenLegacyPlanMappingResolver implements LegacyPlanMappingResolver {

    private final PolicyMigrationProperties properties;

    public ConfigurationDrivenLegacyPlanMappingResolver(PolicyMigrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Long> resolveTargetPlanId(LegacyPolicyRecord legacyPolicyRecord) {
        if (legacyPolicyRecord == null || legacyPolicyRecord.legacyCoveragePlanId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(properties.getLegacyCoveragePlanMap().get(legacyPolicyRecord.legacyCoveragePlanId()));
    }
}
