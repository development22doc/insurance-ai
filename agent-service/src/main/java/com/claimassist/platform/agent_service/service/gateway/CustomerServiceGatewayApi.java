package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import reactor.core.publisher.Mono;

public interface CustomerServiceGatewayApi {
    PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId);

    /**
     * Reactive variant that propagates JWT via WebClient for use in AI tool execution
     * which runs on a non-reactive executor where Feign's interceptor cannot access
     * the reactive SecurityContext.
     */
    Mono<PolicyCoverageDto> getPolicyCoverageReactive(Long policyId, Long targetUserId, String jwtToken);
}
