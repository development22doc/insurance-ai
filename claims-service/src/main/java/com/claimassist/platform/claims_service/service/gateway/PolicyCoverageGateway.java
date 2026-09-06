package com.claimassist.platform.claims_service.service.gateway;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;

public interface PolicyCoverageGateway {
    PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId);
}
