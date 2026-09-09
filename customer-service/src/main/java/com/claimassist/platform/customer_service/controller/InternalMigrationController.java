package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.customer_service.migration.LegacyPolicyDataset;
import com.claimassist.platform.customer_service.service.migration.MigrationExtractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/policies")
@RequiredArgsConstructor
public class InternalMigrationController {

    private final MigrationExtractService migrationExtractService;

    /**
     * POST /internal/v1/policies/extract-for-migration
     * Query params: page (default 0), size (default 100), optional batchId
     * Secured: only trusted service client ids via MigrationExtractService
     */
    @PostMapping("/extract-for-migration")
    public LegacyPolicyDataset extractForMigration(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size,
            @RequestParam(name = "batchId", required = false) String batchId
    ) {
        return migrationExtractService.extractPage(page, size, batchId);
    }
}
