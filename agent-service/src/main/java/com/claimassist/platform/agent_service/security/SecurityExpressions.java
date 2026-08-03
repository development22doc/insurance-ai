package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * agent-service holds no claim-party data of its own - "can this user talk to
 * the agent about this claim" is delegated entirely to claims-service (the
 * single source of truth for claim RBAC), via the resilient
 * ClaimsServiceGateway (fails closed on any error - see its Javadoc).
 */
@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

    private final ClaimsServiceGateway claimsServiceGateway;

    public boolean canAccessClaim(Long claimId) {
        return claimsServiceGateway.checkPermission(claimId, ClaimPermission.VIEW);
    }
}
