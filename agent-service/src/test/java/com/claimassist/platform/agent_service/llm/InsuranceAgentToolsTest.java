package com.claimassist.platform.agent_service.llm;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.PolicyCoverageGateway;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsuranceAgentToolsTest {

    private final ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
    private final PolicyCoverageGateway policyCoverageGateway = mock(PolicyCoverageGateway.class);
    private final List<InsuranceAgentTools.ProposedUpdate> proposed = new ArrayList<>();
    private final ToolRegistry registry = new ToolRegistry();
    private InsuranceAgentTools tools;

    @BeforeEach
    void setUp() {
        tools = new InsuranceAgentTools(42L, 7L, 42L, claims, policyCoverageGateway, registry, 1000, proposed::add);
        when(claims.checkPermission(any(), any())).thenReturn(true);
    }

    @Test
    void getClaimStatusReturnsStructuredJson() {
        when(claims.getClaimStatus(42L)).thenReturn(
                new ClaimStatusDto(42L, 7L, "CLM-1", "UNDER_REVIEW", "FIRE", 1000L, null, List.of()));
        String out = tools.getClaimStatus();
        assertThat(out).contains("\"success\":true").contains("\"claimNumber\":\"CLM-1\"");
    }

    @Test
    void getPolicyCoverageReturnsStructuredJson() {
        when(policyCoverageGateway.getPolicyCoverage(7L, 42L)).thenReturn(
                new PolicyCoverageDto(7L, "P-1", "ACTIVE", "HOME", "Basic", 500L, 100000L, null));
        String out = tools.getPolicyCoverage();
        assertThat(out).contains("\"success\":true").contains("\"coverageLimitCents\":100000");
    }

    @Test
    void getClaimDocumentsReturnsStructuredJson() {
        when(claims.getClaimDocuments(42L)).thenReturn(List.of(
                new ClaimDocumentSummaryDto(1L, "POLICE_REPORT", "COMPLETED", "text", 0.1)));
        String out = tools.getClaimDocuments();
        assertThat(out).contains("\"success\":true").contains("\"docType\":\"POLICE_REPORT\"");
    }

    @Test
    void proposeClaimUpdateCollectsProposalAndReturnsSuccess() {
        String out = tools.proposeClaimUpdate("DOCS_REQUESTED", "photos blurry");
        assertThat(out).contains("\"success\":true");
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).proposedStatus()).isEqualTo("DOCS_REQUESTED");
        assertThat(proposed.get(0).note()).isEqualTo("photos blurry");
    }

    @Test
    void getClaimStatusFailureReturnsStructuredFailure() {
        when(claims.getClaimStatus(42L)).thenThrow(new IllegalStateException("down"));
        String out = tools.getClaimStatus();
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"CLAIMS_SERVICE_UNAVAILABLE\"")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void getPolicyCoverageFailureReturnsStructuredFailure() {
        when(policyCoverageGateway.getPolicyCoverage(7L, 42L)).thenThrow(new IllegalStateException("down"));
        String out = tools.getPolicyCoverage();
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"POLICY_SERVICE_UNAVAILABLE\"");
    }

    @Test
    void getClaimDocumentsFailureReturnsStructuredFailure() {
        when(claims.getClaimDocuments(42L)).thenThrow(new IllegalStateException("down"));
        String out = tools.getClaimDocuments();
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"CLAIMS_SERVICE_UNAVAILABLE\"")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void getClaimDocumentsEmptyReturnsStructuredSuccess() {
        when(claims.getClaimDocuments(42L)).thenReturn(List.of());
        String out = tools.getClaimDocuments();
        assertThat(out).contains("\"success\":true");
    }

    @Test
    void proposeClaimUpdateRejectsMissingTargetStatus() {
        String out = tools.proposeClaimUpdate(null, null);
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"INVALID_TOOL_ARGUMENTS\"");
        assertThat(proposed).isEmpty();
    }

    @Test
    void proposeClaimUpdateAcceptsEmptyNoteAsBlank() {
        String out = tools.proposeClaimUpdate("DOCS_REQUESTED", null);
        assertThat(out).contains("\"success\":true");
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).note()).isEmpty();
    }

    // ---------- Phase 2: authorization ----------

    @Test
    void unauthorizedReadToolRejectedWithoutBackendCall() {
        when(claims.checkPermission(any(), any())).thenReturn(false);
        String out = tools.getClaimStatus();
        assertThat(out).contains("\"success\":false").contains("\"errorCode\":\"UNAUTHORIZED\"");
        verify(claims, never()).getClaimStatus(42L);
    }

    @Test
    void unauthorizedWriteToolRejectedWithoutProposal() {
        when(claims.checkPermission(any(), any())).thenReturn(false);
        String out = tools.proposeClaimUpdate("DOCS_REQUESTED", "note");
        assertThat(out).contains("\"success\":false").contains("\"errorCode\":\"UNAUTHORIZED\"");
        assertThat(proposed).isEmpty();
    }

    @Test
    void writeToolRequiresUpdateStatusPermission() {
        when(claims.checkPermission(42L, com.claimassist.platform.common_lib.enums.ClaimPermission.UPDATE_STATUS))
                .thenReturn(true);
        String out = tools.proposeClaimUpdate("DOCS_REQUESTED", "note");
        assertThat(out).contains("\"success\":true");
        assertThat(proposed).hasSize(1);
    }

    // ---------- Phase 2: idempotency ----------

    @Test
    void duplicateProposalOfSameStatusIsCollapsedToSingleEffect() {
        String first = tools.proposeClaimUpdate("DOCS_REQUESTED", "photos blurry");
        String second = tools.proposeClaimUpdate("DOCS_REQUESTED", "photos blurry again");
        assertThat(first).contains("\"success\":true");
        assertThat(second).contains("\"success\":true").contains("\"idempotent\":true");
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).note()).isEqualTo("photos blurry");
    }

    @Test
    void distinctStatusesAreDistinctProposals() {
        tools.proposeClaimUpdate("DOCS_REQUESTED", "need docs");
        tools.proposeClaimUpdate("UNDER_REVIEW", "still reviewing");
        assertThat(proposed).hasSize(2);
    }

    // ---------- Phase 2: not found ----------

    @Test
    void getClaimStatusNotFoundReturnsStructuredNonRetryableFailure() {
        when(claims.getClaimStatus(42L)).thenReturn(
                new ClaimStatusDto(42L, 7L, null, "NOT_FOUND", "UNKNOWN", null, null, List.of()));
        String out = tools.getClaimStatus();
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"CLAIM_NOT_FOUND\"")
                .contains("\"retryable\":false");
    }

    @Test
    void getPolicyCoverageNotFoundReturnsStructuredNonRetryableFailure() {
        when(policyCoverageGateway.getPolicyCoverage(7L, 42L)).thenReturn(
                new PolicyCoverageDto(7L, null, "NOT_FOUND", "UNKNOWN", "UNKNOWN", null, null, null));
        String out = tools.getPolicyCoverage();
        assertThat(out).contains("\"success\":false")
                .contains("\"errorCode\":\"POLICY_NOT_FOUND\"")
                .contains("\"retryable\":false");
    }

    // ---------- Phase 2: invalid input ----------

    @Test
    void invalidClaimIdRejectedWithoutBackendCall() {
        InsuranceAgentTools bad = new InsuranceAgentTools(null, 7L, 42L, claims, policyCoverageGateway, registry, 1000, proposed::add);
        String out = bad.getClaimStatus();
        assertThat(out).contains("\"success\":false").contains("\"errorCode\":\"INVALID_TOOL_ARGUMENTS\"");
        verify(claims, never()).getClaimStatus(any());
    }

    @Test
    void invalidPolicyIdRejectedWithoutBackendCall() {
        InsuranceAgentTools bad = new InsuranceAgentTools(42L, 0L, 42L, claims, policyCoverageGateway, registry, 1000, proposed::add);
        String out = bad.getPolicyCoverage();
        assertThat(out).contains("\"success\":false").contains("\"errorCode\":\"INVALID_TOOL_ARGUMENTS\"");
        verify(policyCoverageGateway, never()).getPolicyCoverage(any(), any());
    }

    @Test
    void invalidProposedStatusRejectedWithoutProposal() {
        String out = tools.proposeClaimUpdate("NOT_A_STATUS", "note");
        assertThat(out).contains("\"success\":false").contains("\"errorCode\":\"INVALID_TOOL_ARGUMENTS\"");
        assertThat(proposed).isEmpty();
    }

    @Test
    void proposedStatusIsNormalizedToUpperCase() {
        String out = tools.proposeClaimUpdate("docs_requested", "note");
        assertThat(out).contains("\"proposedStatus\":\"DOCS_REQUESTED\"");
        assertThat(proposed.get(0).proposedStatus()).isEqualTo("DOCS_REQUESTED");
    }

    // ---------- Phase 2: provenance ----------

    @Test
    void structuredResultsCarrySourceProvenance() {
        when(claims.getClaimStatus(42L)).thenReturn(
                new ClaimStatusDto(42L, 7L, "CLM-1", "UNDER_REVIEW", "FIRE", 1000L, null, List.of()));
        assertThat(tools.getClaimStatus()).contains("\"source\":\"claims-service\"");
        when(policyCoverageGateway.getPolicyCoverage(7L, 42L)).thenReturn(
                new PolicyCoverageDto(7L, "P-1", "ACTIVE", "HOME", "Basic", 500L, 100000L, null));
        assertThat(tools.getPolicyCoverage()).contains("\"source\":\"policy-service\"");
    }

    @Test
    void errorMessagesNeverExposeInternals() {
        when(claims.getClaimStatus(42L)).thenThrow(new RuntimeException("java.sql.SQLException: connection to db:3306 failed"));
        String out = tools.getClaimStatus();
        assertThat(out).doesNotContain("SQLException").doesNotContain("3306")
                .doesNotContain("db").doesNotContain("at com.claimassist");
    }
}