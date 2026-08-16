package com.claimassist.platform.agent_service.cache;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5 security/isolation: authorization is ALWAYS enforced BEFORE the
 * cache is consulted, so a cache hit can never bypass resource ownership.
 * Verified at the tool layer (where authz + gateway read both happen), the
 * exact boundary a real agent request crosses.
 */
class CacheSecurityIsolationTest {

    private final ToolRegistry registry = new ToolRegistry();
    private final ClaimStatusDto status =
            new ClaimStatusDto(99L, 7L, "CLM-99", "UNDER_REVIEW", "FIRE", 1000L, null, List.of());

    private InsuranceAgentTools tools(ClaimsServiceGateway claims, CustomerServiceGateway customer,
                                      AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted) {
        return new InsuranceAgentTools(99L, 7L, 99L, claims, customer, registry, 1000,
                accepted::set);
    }

    @Test
    void unauthorizedUserIsBlockedBeforeAnyCacheableRead() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(anyLong(), eq(ClaimPermission.VIEW))).thenReturn(false);
        when(claims.getClaimStatus(99L)).thenReturn(status); // would be a cache hit, but must not be reached
        AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted = new AtomicReference<>();
        InsuranceAgentTools tools = tools(claims, mock(CustomerServiceGateway.class), accepted);

        String result = tools.getClaimStatus();

        assertThat(result).contains("UNAUTHORIZED");
        // Authorization failed → the cacheable backend read is NEVER invoked,
        // so an unauthorized user can never receive cached data.
        verify(claims, never()).getClaimStatus(anyLong());
    }

    @Test
    void authorizedUserGetsCorrectData() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(anyLong(), eq(ClaimPermission.VIEW))).thenReturn(true);
        when(claims.getClaimStatus(99L)).thenReturn(status);
        AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted = new AtomicReference<>();
        InsuranceAgentTools tools = tools(claims, mock(CustomerServiceGateway.class), accepted);

        String result = tools.getClaimStatus();

        assertThat(result).contains("UNDER_REVIEW");
        verify(claims).getClaimStatus(99L);
    }

    @Test
    void differentClaimResourceIsIndependent() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(anyLong(), eq(ClaimPermission.VIEW))).thenReturn(true);
        when(claims.getClaimStatus(99L)).thenReturn(status);
        AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted = new AtomicReference<>();
        // Tools are request-scoped to one claim; another claim uses another instance.
        InsuranceAgentTools t99 = tools(claims, mock(CustomerServiceGateway.class), accepted);
        InsuranceAgentTools t200 = new InsuranceAgentTools(200L, 7L, 200L, claims,
                mock(CustomerServiceGateway.class), registry, 1000, accepted::set);
        when(claims.getClaimStatus(200L)).thenReturn(
                new ClaimStatusDto(200L, 7L, "CLM-200", "CLOSED", "FIRE", 0L, 100L, List.of()));

        assertThat(t99.getClaimStatus()).contains("UNDER_REVIEW");
        assertThat(t200.getClaimStatus()).contains("CLOSED");
    }

    @Test
    void acceptedProposalInvalidatesReadCachesAfterTheWrite() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(anyLong(), eq(ClaimPermission.UPDATE_STATUS))).thenReturn(true);
        AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted = new AtomicReference<>();
        InsuranceAgentTools tools = tools(claims, mock(CustomerServiceGateway.class), accepted);

        String result = tools.proposeClaimUpdate("DOCS_REQUESTED", "photos were blurry");

        assertThat(result).contains("\"success\":true");
        assertThat(accepted.get()).isNotNull();
        // Invalidation happens only AFTER the write is accepted.
        verify(claims).evictClaimStatus(99L);
    }

    @Test
    void rejectedProposalDoesNotInvalidate() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(anyLong(), eq(ClaimPermission.UPDATE_STATUS))).thenReturn(false);
        AtomicReference<InsuranceAgentTools.ProposedUpdate> accepted = new AtomicReference<>();
        InsuranceAgentTools tools = tools(claims, mock(CustomerServiceGateway.class), accepted);

        String result = tools.proposeClaimUpdate("APPROVED", "n/a");

        assertThat(result).contains("UNAUTHORIZED");
        verify(claims, never()).evictClaimStatus(anyLong());
    }
}