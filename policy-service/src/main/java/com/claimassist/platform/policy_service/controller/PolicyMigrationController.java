package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.migration.ConfigurationDrivenLegacyPlanMappingResolver;
import com.claimassist.platform.policy_service.migration.LegacyPolicyDataset;
import com.claimassist.platform.policy_service.migration.LegacyPolicyDryRunReport;
import com.claimassist.platform.policy_service.migration.LegacyPolicyDryRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policies/migration")
@RequiredArgsConstructor
public class PolicyMigrationController {

    private final LegacyPolicyDryRunService dryRunService;
    private final ConfigurationDrivenLegacyPlanMappingResolver mappingResolver;

    @PostMapping("/dry-run")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<LegacyPolicyDryRunReport> dryRun(@Valid @RequestBody LegacyPolicyDataset dataset) {
        LegacyPolicyDryRunReport report = dryRunService.dryRun(dataset, mappingResolver);
        return ResponseEntity.ok(report);
    }
}
