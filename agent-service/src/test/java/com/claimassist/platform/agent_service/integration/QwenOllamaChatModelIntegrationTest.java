package com.claimassist.platform.agent_service.integration;

import com.claimassist.platform.agent_service.ai.tool.ToolExecutionGuard;
import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.llm.PromptUtils;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.support.OllamaTestSupport;
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
 * Exercises the {@link com.claimassist.platform.agent_service.ai.model.QwenOllamaChatModel}
 * end-to-end against a real local Ollama running qwen2.5-coder:3b. qwen emits
 * its tool call as JSON text inside assistant content spread across streaming
 * chunks; this proves the model accumulates, detects, suppresses and executes
 * that call through the existing guarded pipeline. Skipped automatically when
 * no model is reachable.
 */
@Tag("ollama")
class QwenOllamaChatModelIntegrationTest {

    private static ChatClient chatClient;

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
        when(claims.checkPermission(anyLong(), any())).thenReturn(true);
        when(claims.getClaimStatus(99L)).thenReturn(new ClaimStatusDto(
                99L, 7L, "CLM-9921", "UNDER_REVIEW", "FIRE", 12000L, null,
                List.of(new ClaimStatusDto.StatusHistoryEntry("SUBMITTED", "UNDER_REVIEW", "system", "2026-01-01"))));
        when(customer.getPolicyCoverage(7L, 99L)).thenReturn(new PolicyCoverageDto(
                7L, "POL-77", "ACTIVE", "HOME", "Guard", 500L, 250000L, "2027-01-01"));
        when(claims.getClaimDocuments(99L)).thenReturn(List.of(
                new ClaimDocumentSummaryDto(1L, "POLICE_REPORT", "COMPLETED", "incident details", 0.05)));
    }

    private String ask(String question) {
        InsuranceAgentTools tools = new InsuranceAgentTools(99L, 7L, 99L, claims, customer, new ToolRegistry(), 1000, proposed::add);
        AgentAiProperties props = new AgentAiProperties();
        props.setAgentTimeoutMs(120_000);
        ToolCallbackProvider provider =
                new ToolExecutionGuard(props, new ToolRegistry())
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
    void getClaimStatusTextToolCallExecutedAndNotLeaked() {
        String answer = ask("What is the current status of my claim?");
        assertThat(answer).isNotBlank();
        assertThat(answer).doesNotContain("\"name\"", "\"arguments\"");
        verify(claims, atLeast(1)).getClaimStatus(99L);
    }

    @Test
    void getPolicyCoverageTextToolCallExecuted() {
        String answer = ask("What coverage do I have on my policy?");
        assertThat(answer.trim().length()).isGreaterThan(20);
        verify(customer, atLeast(1)).getPolicyCoverage(7L, 99L);
    }

    @Test
    void getClaimDocumentsTextToolCallExecuted() {
        String answer = ask("Show me the documents I have submitted for my claim.");
        assertThat(answer.trim().length()).isGreaterThan(20);
        verify(claims, atLeast(1)).getClaimDocuments(99L);
    }

    @Test
    void proposeClaimUpdateViaTextToolCall() {
        String answer = ask("Please propose moving my claim to DOCS_REQUESTED because my photos are too blurry to read.");
        assertThat(answer).isNotBlank();
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).proposedStatus()).isEqualTo("DOCS_REQUESTED");
    }

    @Test
    void multipleSequentialTextToolCallsForMultiPartQuestion() {
        String answer = ask("What is my claim status and which documents have I submitted?");
        assertThat(answer.trim().length()).isGreaterThan(20);
        verify(claims, atLeast(1)).getClaimStatus(99L);
        verify(claims, atLeast(1)).getClaimDocuments(99L);
    }

    @Test
    void normalTextResponseStreamsUnchanged() {
        String answer = ask("Say hello in one short sentence.");
        assertThat(answer).isNotBlank();
        assertThat(answer).doesNotContain("\"name\"", "\"arguments\"");
    }
}
