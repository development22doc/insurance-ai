package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;

/**
 * Service abstraction for policy coverage lookup. Phase-1: scaffold only.
 * Implementations will later use claimassist_policy as the data source.
 */
public interface PolicyCoverageQueryService {

    /**
     * Returns PolicyCoverageDto for the given policyId and callingUserIdHeader
     * If the service cannot determine coverage in Phase 1 the implementation may
     * return null or throw an exception to result in 503.
     */
    PolicyCoverageDto getPolicyCoverage(Long policyId, String xUserIdHeader);
}
