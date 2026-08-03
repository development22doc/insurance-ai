package com.claimassist.platform.common_lib.dto;

/**
 * Deliberately narrow: this is ALL an AI agent tool call is allowed to see
 * about a policy. No SSN, no payment method, no full customer profile - PII
 * minimization enforced at the contract level, not left to prompt discipline.
 */
public record PolicyCoverageDto(
        Long policyId,
        String policyNumber,
        String status,
        String productType,
        String coveragePlanName,
        Long deductibleCents,
        Long coverageLimitCents,
        String renewalDate
) {}
