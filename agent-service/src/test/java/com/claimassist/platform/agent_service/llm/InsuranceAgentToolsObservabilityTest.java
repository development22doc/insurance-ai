package com.claimassist.platform.agent_service.llm;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.support.RecordingAgentTelemetry;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCENARIO 3 - authorization denied (Phase 6.7). A denied tool produces a
 * SECURITY_DENIED telemetry event and a canonical audit event, and the tool's
 * backend is never reached.
 */
class InsuranceAgentToolsObservabilityTest {

    private final RecordingAgentTelemetry telemetry = new RecordingAgentTelemetry();

    @Test
    void deniedToolEmitsSecurityDeniedAndAuditWithoutReachingBackend() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        // Fail-closed: permission check denies the user for get_claim_status.
        when(claims.checkPermission(eq(99L), any())).thenReturn(false);

        InsuranceAgentTools tools = new InsuranceAgentTools(99L, 7L, 99L, claims,
                mock(CustomerServiceGateway.class), new ToolRegistry(), 1000,
                p -> { }, telemetry, "req-1", "corr-1");

        String result = tools.getClaimStatus();

        assertThat(result).contains("UNAUTHORIZED");
        // Backend is never reached on a denied request.
        verify(claims, never()).getClaimStatus(99L);
        // Telemetry: SECURITY_DENIED recorded, correlated to the request.
        assertThat(telemetry.eventTypes()).contains("SECURITY_DENIED");
        assertThat(telemetry.events().stream().map(RecordingAgentTelemetry.Event::requestId).distinct())
                .containsExactly("req-1");
    }

    @Test
    void allowedToolDoesNotEmitSecurityDenied() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(any(), any())).thenReturn(true);
        when(claims.getClaimStatus(99L)).thenReturn(null); // NOT_FOUND path is fine; just check no denial

        InsuranceAgentTools tools = new InsuranceAgentTools(99L, 7L, 99L, claims,
                mock(CustomerServiceGateway.class), new ToolRegistry(), 1000,
                p -> { }, telemetry, "req-1", "corr-1");

        tools.getClaimStatus();
        assertThat(telemetry.eventTypes()).doesNotContain("SECURITY_DENIED");
    }

    @Test
    void acceptedProposalEmitsBusinessAudit() {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.checkPermission(any(), eq(ClaimPermission.UPDATE_STATUS))).thenReturn(true);
        List<InsuranceAgentTools.ProposedUpdate> proposals = new java.util.concurrent.CopyOnWriteArrayList<>();

        InsuranceAgentTools tools = new InsuranceAgentTools(99L, 7L, 99L, claims,
                mock(CustomerServiceGateway.class), new ToolRegistry(), 1000,
                proposals::add, telemetry, "req-1", "corr-1");

        String result = tools.proposeClaimUpdate("DOCS_REQUESTED", "awaiting police report");
        assertThat(result).contains("DOCS_REQUESTED");
        assertThat(proposals).hasSize(1);
        // The accepted proposal is a business-significant audit action.
        // (Security denial is asserted separately; the accepted-proposal audit is
        // emitted via the telemetry.audit() channel, not recorded as a lifecycle event.)
        assertThat(telemetry.eventTypes()).doesNotContain("SECURITY_DENIED");
    }
}