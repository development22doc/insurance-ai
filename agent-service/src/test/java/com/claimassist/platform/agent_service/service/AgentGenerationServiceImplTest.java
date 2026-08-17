package com.claimassist.platform.agent_service.service;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.service.impl.AgentGenerationServiceImpl;
import com.claimassist.platform.agent_service.support.AgentTelemetryTestSupport;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGenerationServiceImplTest {

    /** Builds a ChatClient whose stream returns the given content chunks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ChatClient chatClientWith(Flux<String> content) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(client.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.toolCallbacks(any(org.springframework.ai.tool.ToolCallbackProvider.class))).thenReturn(spec);
        when(spec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(content);
        return client;
    }

    private AgentGenerationServiceImpl service(ChatClient client, AgentTurnPersistence persistence) {
        AgentAiProperties props = new AgentAiProperties();
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(42L);
        AgentSessionRepository sessionRepo = mock(AgentSessionRepository.class);
        AgentSession session = AgentSession.builder().id(new AgentSessionId(99L, 42L)).build();
        when(sessionRepo.findById(any())).thenReturn(Optional.of(session));
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        when(claims.getClaimStatus(99L))
                .thenReturn(new ClaimStatusDto(99L, 7L, "CLM-99", "UNDER_REVIEW", "FIRE", 1000L, null, List.of()));
        CustomerServiceGateway customer = mock(CustomerServiceGateway.class);
        return new AgentGenerationServiceImpl(
                client, props, currentUser, sessionRepo, persistence, new ToolRegistry(), claims, customer,
                new com.claimassist.platform.agent_service.security.InputGuardrails(props),
                new com.claimassist.platform.agent_service.security.OutputGuardrails(props),
                new com.claimassist.platform.agent_service.memory.ConversationMemoryService(
                        mock(com.claimassist.platform.agent_service.repository.AgentMessageRepository.class), props),
                new com.claimassist.platform.agent_service.memory.ConversationContextBuilder(props),
                AgentTelemetryTestSupport.telemetry());
    }

    @Test
    void streamsTokensThenEmitsDoneEventAndPersistsFullText() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(chatClientWith(Flux.just("Hello ", "world", "!")), persistence);

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(StreamResponse::eventType)
                .containsExactly("message", "message", "message", "done");
        assertThat(events).extracting(StreamResponse::text)
                .containsExactly("Hello ", "world", "!", "");
        assertThat(events.get(0).requestId()).isNotBlank();
        assertThat(events.get(3).done()).isTrue();

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistence, timeout(5000)).finalizeTurn(anyString(), any(), textCaptor.capture(),
                anyLong(), any(), anyLong(), any(), any());
        assertThat(textCaptor.getValue()).isEqualTo("Hello world!");
    }

    @Test
    void emitsControlledErrorEventOnStreamFailure() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(
                chatClientWith(Flux.error(new IllegalStateException("model exploded"))), persistence);

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("error");
        assertThat(events.get(0).errorCode()).isEqualTo("MODEL_ERROR");
        assertThat(events.get(0).text()).doesNotContain("model exploded")
                .doesNotContain("IllegalStateException");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistence, timeout(5000)).finalizeTurn(anyString(), any(), textCaptor.capture(),
                anyLong(), any(), anyLong(), any(), any());
        assertThat(textCaptor.getValue()).doesNotContain("model exploded");
    }

    @Test
    void emitsOllamaUnavailableOnConnectionError() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(
                chatClientWith(Flux.error(new java.net.ConnectException("Connection refused"))), persistence);

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();

        assertThat(events.get(0).errorCode()).isEqualTo("OLLAMA_UNAVAILABLE");
        verify(persistence, timeout(5000)).finalizeTurn(anyString(), any(), anyString(),
                anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void doesNotPersistTwiceOnCleanCompletion() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(chatClientWith(Flux.just("ok")), persistence);
        svc.streamResponse("hi", 99L).collectList().block();
        verify(persistence, timeout(5000)).finalizeTurn(anyString(), any(), anyString(),
                anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void filtersBlankChunksButKeepsTerminalDoneEvent() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(
                chatClientWith(Flux.just(" ", "", "hello", "\t", "world")), persistence);

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(StreamResponse::text)
                .containsExactly("hello", "world", "");
        assertThat(events).extracting(StreamResponse::eventType)
                .containsExactly("message", "message", "done");
    }

    @Test
    void emptyStreamStillEmitsDoneAndPersistsFallbackMessage() {
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AgentGenerationServiceImpl svc = service(chatClientWith(Flux.empty()), persistence);

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(StreamResponse::eventType).containsExactly("done");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistence, timeout(5000)).finalizeTurn(anyString(), any(), textCaptor.capture(),
                anyLong(), any(), anyLong(), any(), any());
        assertThat(textCaptor.getValue()).isEqualTo("The assistant is currently unavailable. Please try again shortly.");
    }
}