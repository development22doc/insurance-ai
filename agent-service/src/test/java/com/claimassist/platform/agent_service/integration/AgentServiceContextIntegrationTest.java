package com.claimassist.platform.agent_service.integration;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.AgentTurnPersistence;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.service.impl.AgentGenerationServiceImpl;
import com.claimassist.platform.agent_service.support.OllamaTestSupport;
import com.claimassist.platform.agent_service.support.AgentTelemetryTestSupport;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REAL Spring-context integration test of the agent's request path.
 * <p>
 * REAL: Spring ApplicationContext (wiring of the real service bean), real
 * Spring AI ChatClient, real Ollama, real tool callbacks, real SSE Flux.
 * MOCKED (documented): external microservice gateways, session repository,
 * persistence and the user provider - components owned by other services or
 * requiring auth/DB infrastructure. This verifies the genuine
 * Service → Spring AI → Ollama → tool → result → LLM → SSE boundary, which
 * the isolated real-Ollama tests do not (they bypass the service).
 * <p>
 * The reactive error path (LLM/tool failure → controlled SSE error event) is
 * covered at unit level in AgentGenerationServiceImplTest; here we verify the
 * real happy path against a live model.
 */
@SpringJUnitConfig(AgentServiceContextIntegrationTest.Config.class)
@Tag("ollama")
class AgentServiceContextIntegrationTest {

    @Autowired
    private AgentGenerationService service;

    @Autowired
    private ClaimsServiceGateway claimsGateway;

    @Autowired
    private AgentTurnPersistence persistence;

    @Test
    void streamsRealAgentTurnWithToolCallCompletionAndPersistence() {
        OllamaTestSupport.assumeOllamaAvailable();

        List<StreamResponse> list = service.streamResponse(
                "What is the current status of my claim?", 99L).collectList().block();

        assertThat(list).isNotNull();
        // At least one streamed token + a terminal done event.
        assertThat(list).extracting(StreamResponse::eventType).contains("message", "done");
        StreamResponse terminal = list.getLast();
        assertThat(terminal.eventType()).isEqualTo("done");
        assertThat(terminal.done()).isTrue();

        // The model genuinely selected + executed the tool via Spring AI/Ollama.
        verify(claimsGateway, atLeast(1)).getClaimStatus(99L);
        // The turn was persisted asynchronously.
        verify(persistence, timeout(5000)).finalizeTurn(
                org.mockito.ArgumentMatchers.anyString(),
                any(), org.mockito.ArgumentMatchers.anyString(),
                anyLong(), any(), anyLong(), any(), any());
    }

    @Configuration
    static class Config {

        @Bean
        AgentAiProperties agentAiProperties() {
            AgentAiProperties props = new AgentAiProperties();
            // A cold 1.7B model doing tool calling + streaming can exceed the
            // production 60s budget under a full-suite load; give the real
            // integration test more headroom so it verifies completion rather
            // than timing out. Production limits are unchanged.
            props.setAgentTimeoutMs(120_000);
            return props;
        }

        @Bean
        ToolRegistry toolRegistry() {
            return new ToolRegistry();
        }

        @Bean
        com.claimassist.platform.agent_service.observability.AgentTelemetry agentTelemetry() {
            return AgentTelemetryTestSupport.telemetry();
        }

        /** Real Spring AI ChatClient wired to a real Ollama instance. */
        @Bean
        ChatClient chatClient() {
            return OllamaTestSupport.chatClient();
        }

        @Bean
        CurrentUserProvider currentUserProvider() {
            CurrentUserProvider mock = mock(CurrentUserProvider.class);
            when(mock.getCurrentUserId()).thenReturn(1L);
            return mock;
        }

        @Bean
        ClaimsServiceGateway claimsServiceGateway() {
            ClaimsServiceGateway mock = mock(ClaimsServiceGateway.class);
            when(mock.getClaimStatus(99L)).thenReturn(
                    new ClaimStatusDto(99L, 7L, "CLM-99", "UNDER_REVIEW", "FIRE", 1000L, null, List.of()));
            when(mock.checkPermission(anyLong(), any())).thenReturn(true);
            return mock;
        }

        @Bean
        CustomerServiceGateway customerServiceGateway() {
            return mock(CustomerServiceGateway.class);
        }

        @Bean
        AgentSessionRepository agentSessionRepository() {
            AgentSessionRepository mock = mock(AgentSessionRepository.class);
            when(mock.findById(any())).thenReturn(
                    Optional.of(AgentSession.builder().id(new AgentSessionId(99L, 1L)).build()));
            return mock;
        }

        @Bean
        AgentTurnPersistence agentTurnPersistence() {
            return mock(AgentTurnPersistence.class);
        }

        @Bean
        com.claimassist.platform.agent_service.security.InputGuardrails inputGuardrails(AgentAiProperties props) {
            return new com.claimassist.platform.agent_service.security.InputGuardrails(props);
        }

        @Bean
        com.claimassist.platform.agent_service.security.OutputGuardrails outputGuardrails(AgentAiProperties props) {
            return new com.claimassist.platform.agent_service.security.OutputGuardrails(props);
        }

        @Bean
        com.claimassist.platform.agent_service.memory.ConversationMemoryService conversationMemoryService(
                com.claimassist.platform.agent_service.repository.AgentMessageRepository messageRepo, AgentAiProperties props) {
            return new com.claimassist.platform.agent_service.memory.ConversationMemoryService(messageRepo, props);
        }

        @Bean
        com.claimassist.platform.agent_service.memory.ConversationContextBuilder conversationContextBuilder(AgentAiProperties props) {
            return new com.claimassist.platform.agent_service.memory.ConversationContextBuilder(props);
        }

        @Bean
        com.claimassist.platform.agent_service.repository.AgentMessageRepository agentMessageRepository() {
            return mock(com.claimassist.platform.agent_service.repository.AgentMessageRepository.class);
        }

        @Bean
        AgentGenerationService agentGenerationService(ChatClient chatClient,
                AgentAiProperties props, ToolRegistry toolRegistry, CurrentUserProvider currentUser,
                AgentSessionRepository sessionRepo, AgentTurnPersistence persistence,
                ClaimsServiceGateway claims, CustomerServiceGateway customer,
                com.claimassist.platform.agent_service.security.InputGuardrails inputGuardrails,
                com.claimassist.platform.agent_service.security.OutputGuardrails outputGuardrails,
                com.claimassist.platform.agent_service.memory.ConversationMemoryService conversationMemoryService,
                com.claimassist.platform.agent_service.memory.ConversationContextBuilder conversationContextBuilder,
                com.claimassist.platform.agent_service.observability.AgentTelemetry agentTelemetry) {
            return new AgentGenerationServiceImpl(chatClient, props, currentUser, sessionRepo,
                    persistence, toolRegistry, claims, customer, inputGuardrails, outputGuardrails,
                    conversationMemoryService, conversationContextBuilder, agentTelemetry);
        }
    }
}