package com.claimassist.platform.policy_service.migration;

/**
 * Legacy Policy Extraction Contract
 *
 * This document defines the required extraction contract for migrating legacy Customer Service
 * policies to Policy Service. The extraction must be performed by Customer Service through
 * a controlled boundary, not by direct database access from Policy Service.
 *
 * ARCHITECTURE CONSTRAINTS:
 * - Policy Service must NOT directly access Customer Service's database
 * - Extraction must happen at Customer Service service layer or API boundary
 * - Data transfer must use DTOs, not direct entity coupling
 * - No mutation of source data during extraction
 *
 * EXTRACTION SOURCE:
 * Customer Service should implement extraction at the service layer or expose an
 * internal API endpoint that returns LegacyPolicyDataset format.
 *
 * REQUIRED FIELDS FOR EACH LEGACY POLICY:
 * - legacyPolicyId: Customer Policy.id
 * - legacyCustomerId: Customer.id
 * - legacyPolicyNumber: Policy.policyNumber
 * - legacyCoveragePlanId: CoveragePlan.id
 * - legacyCoveragePlanName: CoveragePlan.name
 * - productType: CoveragePlan.productType
 * - annualPremiumCents: CoveragePlan.annualPremiumCents
 * - deductibleCents: CoveragePlan.deductibleCents
 * - coverageLimitCents: CoveragePlan.coverageLimitCents
 * - stripePriceId: CoveragePlan.stripePriceId (if present)
 * - stripeSubscriptionId: Policy.stripeSubscriptionId (if present)
 * - effectiveDate: Policy.effectiveDate
 * - renewalDate: Policy.renewalDate (if present)
 * - legacyStatus: Policy.status
 * - sourceSystem: "customer_service"
 *
 * EXTRACTED DATASET REQUIREMENTS:
 * - Must include all policies to be migrated
 * - Must be sorted by legacyPolicyId for deterministic checksum
 * - Must include checksum computed by LegacyPolicyDataset.computeChecksum()
 * - Must include batchId for tracking
 * - Must include extractedAt timestamp
 * - Must include schemaVersion
 *
 * EXTRACTION METHODS (Customer Service Responsibility):
 * Option 1: Service Method
 * - CustomerService.extractLegacyPoliciesForMigration() → LegacyPolicyDataset
 *
 * Option 2: Internal API Endpoint
 * - POST /internal/policies/extract-for-migration → LegacyPolicyDataset
 * - Secured with internal service authentication
 *
 * IMPLEMENTATION STATUS:
 * This contract documents the requirement. Actual extraction implementation
 * is deferred until business authorization and Customer Service readiness.
 *
 * For Phase 4 testing, synthetic LegacyPolicyDataset will be used directly
 * via StaticLegacyPolicySource or similar test fixtures.
 */
public interface LegacyPolicyExtractionContract {
    // This is a documentation interface only - no implementation required in Phase 4
}
