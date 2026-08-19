package com.claimassist.platform.agent_service.evaluation;

import com.claimassist.platform.agent_service.ai.tool.ToolExecutionGuard;
import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.llm.PromptUtils;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.support.OllamaTestSupport;
import com.claimassist.platform.agent_service.support.RecordingAgentTelemetry;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real-Ollama evaluation (Phase 6.23 / 6.25): uses the deterministic dataset
 * and {@link AgentEvaluator} to assert TOOL_SELECTION structurally against a
 * genuine model, alongside telemetry from {@link ToolExecutionGuard}. Skipped
 * when no local model is reachable. Assertions are structural (tool selected),
 * never fragile prose equality.
 */
@Tag("ollama")
class AgentOllamaEvaluationIntegrationTest {

    private static ChatClient chatClient;
    private final RecordingAgentTelemetry telemetry = new RecordingAgentTelemetry();

    private ClaimsServiceGateway claims;
    private CustomerServiceGateway customer;
    private final List<InsuranceAgentTools.ProposedUpdate> proposed = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void init() {
        OllamaTestSupport.assumeOllamaAvailable();
        chatClient = OllamaTestSupport.chatClient();
    }

    @BeforeEach
    void setUp() {
        claims = mock(ClaimsServiceGateway.class);
        customer = mock(CustomerServiceGateway.class);
        proposed.clear();
        telemetry.clear();
        when(claims.checkPermission(anyLong(), any())).thenReturn(true);
        when(claims.getClaimStatus(99L)).thenReturn(new ClaimStatusDto(
                99L, 7L, "CLM-9921", "UNDER_REVIEW", "FIRE", 12000L, null, List.of()));
        when(customer.getPolicyCoverage(7L, 99L)).thenReturn(new PolicyCoverageDto(
                7L, "POL-77", "ACTIVE", "HOME", "Guard", 500L, 250000L, "2027-01-01"));
        when(claims.getClaimDocuments(99L)).thenReturn(List.of(
                new ClaimDocumentSummaryDto(1L, "POLICE_REPORT", "COMPLETED", "incident details", 0.05)));
    }

    private String ask(String question) {
        InsuranceAgentTools tools = new InsuranceAgentTools(99L, 7L, 99L, claims, customer, new ToolRegistry(), 1000,
                proposed::add, telemetry, "eval-req-1", "eval-corr-1");
        AgentAiProperties props = new AgentAiProperties();
        props.setAgentTimeoutMs(120_000);
        ToolCallbackProvider provider = new ToolExecutionGuard(props, new ToolRegistry(),
                "eval-req-1", "eval-corr-1", meta -> { }, telemetry, 99L, 1L, "claimHash")
                .guarded(MethodToolCallbackProvider.builder().toolObjects(tools).build());

        List<String> chunks = chatClient.prompt()
                .system(PromptUtils.INSURANCE_AGENT_SYSTEM_PROMPT)
                .user(question)
                .toolCallbacks(provider)
                .stream()
                .content()
                .collectList()
                .block(Duration.ofSeconds(120));
        return chunks == null ? "" : String.join("", chunks);
    }

    @Test
    void toolSelectionCasePassesAgainstRealModel() {
        String answer = ask("Show me the status of my claim.");
        assertThat(answer.trim().length()).isGreaterThan(20);

        verify(claims, atLeast(1)).getClaimStatus(99L);
        EvaluationResult result = AgentEvaluator.evaluate(
                EvaluationDataset.byId("tool-status"),
                new AgentOutcome("get_claim_status", false, false, false, false, null));
        assertThat(result.passed()).withFailMessage("real-model evaluation failed: %s", result.reasons()).isTrue();
        // Tool telemetry was emitted and correlated.
        assertThat(telemetry.eventTypes()).contains("TOOL_COMPLETED");
    }
}