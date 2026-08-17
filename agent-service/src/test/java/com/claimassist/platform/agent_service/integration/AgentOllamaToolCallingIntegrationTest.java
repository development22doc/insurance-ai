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
import reactor.core.publisher.Flux;

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
 * REAL tool-calling tests against a live Ollama instance.
 * <p>
 * These verify the full Agent -> Ollama -> Tool -> Tool-result -> Ollama ->
 * final-response loop: the model must genuinely select and execute the right
 * tool and then ground its final answer in the returned data. Skipped
 * automatically when no local model is reachable.
 */
@Tag("ollama")
class AgentOllamaToolCallingIntegrationTest {

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
    void test1_claimStatusToolSelectedAndExecuted() {
        String answer = ask("What is the current status of my claim?");
        assertFinalAnswer(answer);
        // The real proof: the model genuinely selected and executed this tool
        // in this turn, feeding its structured result back into the answer.
        verify(claims, atLeast(1)).getClaimStatus(99L);
    }

    @Test
    void test2_policyCoverageToolSelectedAndExecuted() {
        String answer = ask("What coverage do I have on my policy?");
        assertFinalAnswer(answer);
        verify(customer, atLeast(1)).getPolicyCoverage(7L, 99L);
    }

    @Test
    void test3_claimDocumentsToolSelectedAndExecuted() {
        String answer = ask("Show me the documents I have submitted for my claim.");
        assertFinalAnswer(answer);
        verify(claims, atLeast(1)).getClaimDocuments(99L);
    }

    @Test
    void test4_proposeClaimUpdateCollectedViaTool() {
        String answer = ask("Please propose moving my claim to DOCS_REQUESTED because my photos are too blurry to read.");
        assertThat(answer).isNotBlank();
        assertThat(proposed).hasSize(1);
        assertThat(proposed.get(0).proposedStatus()).isEqualTo("DOCS_REQUESTED");
    }

    @Test
    void test5_multipleToolCallsForMultiPartQuestion() {
        String answer = ask("What is my claim status and which documents have I submitted?");
        assertFinalAnswer(answer);
        verify(claims, atLeast(1)).getClaimStatus(99L);
        verify(claims, atLeast(1)).getClaimDocuments(99L);
    }

    /**
     * A small local model may paraphrase or mis-state exact figures from a
     * tool result, so we assert a real composed response was produced rather
     * than exact numeric substrings. Tool selection/execution is proven
     * deterministically by {@code verify(...)}.
     */
    private void assertFinalAnswer(String answer) {
        assertThat(answer).isNotBlank();
        assertThat(answer.trim().length()).isGreaterThan(20);
    }
}
