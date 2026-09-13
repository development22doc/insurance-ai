package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;

import java.util.List;

public interface ClaimsServiceGatewayApi {
    ClaimStatusDto getClaimStatus(Long claimId);

    reactor.core.publisher.Mono<ClaimStatusDto> getClaimStatusReactive(Long claimId);

    /**
     * Reactive claim status fetch that accepts an explicit JWT token instead of relying on
     * ReactiveSecurityContextHolder. Use this from AI tool execution which runs
     * on a non-reactive executor where the SecurityContext may not be available.
     */
    reactor.core.publisher.Mono<ClaimStatusDto> getClaimStatusReactive(Long claimId, String jwtToken);

    List<ClaimDocumentSummaryDto> getClaimDocuments(Long claimId);

    boolean checkPermission(Long claimId, ClaimPermission permission);

    reactor.core.publisher.Mono<Boolean> checkPermissionReactive(Long claimId, ClaimPermission permission);

    /**
     * Permission check that accepts an explicit JWT token instead of relying on
     * ReactiveSecurityContextHolder. Use this from AI tool execution which runs
     * on a non-reactive executor where the SecurityContext may not be available.
     */
    reactor.core.publisher.Mono<Boolean> checkPermissionWithToken(Long claimId, ClaimPermission permission, String jwtToken);

    void evictClaimStatus(Long claimId);
}
