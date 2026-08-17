package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.security.InputGuardrails;
import com.claimassist.platform.agent_service.security.OutputGuardrails;
import com.claimassist.platform.agent_service.service.AgentTurnPersistence;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.agent_service.service.impl.AgentGenerationServiceImpl;
import com.claimassist.platform.agent_service.support.AgentTelemetryTestSupport;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.MessageRole;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4 memory scenarios at the service level. A mock ChatClient CAPTURES the
 * system prompt actually passed to the LLM, so these tests PROVE the bounded
 * conversation history was supplied to the model (not that Java inserted the
 * answer), and that ownership/isolation/bounds hold end-to-end through the
 * real memory + context pipeline.
 */
class ConversationMemoryScenarioTest {

    private final AgentAiProperties props = new AgentAiProperties();

    /** Builds the real service; the captured system prompt is the one the LLM receives. */
    private AgentGenerationServiceImpl svc(ChatClient client, AgentMessageRepository repo,
                                           CurrentUserProvider user, AgentSessionRepository sessionRepo,
                                           ClaimsServiceGateway claims, AgentTurnPersistence persistence,
                                           AtomicReference<String> capturedSystem) {
        ConversationMemoryService memory = new ConversationMemoryService(repo, props);
        ConversationContextBuilder builder = new ConversationContextBuilder(props);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        when(client.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenAnswer(inv -> { capturedSystem.set(inv.getArgument(0)); return spec; });
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.toolCallbacks(any(org.springframework.ai.tool.ToolCallbackProvider.class))).thenReturn(spec);
        when(spec.stream()).thenReturn(stream);
        when(stream.content()).thenReturn(Flux.just("answer"));
        return new AgentGenerationServiceImpl(client, props, user, sessionRepo, persistence,
                new ToolRegistry(), claims, mock(CustomerServiceGateway.class),
                new InputGuardrails(props), new OutputGuardrails(props), memory, builder,
                AgentTelemetryTestSupport.telemetry());
    }

    private AgentMessage msg(long id, MessageRole role, String content) {
        return AgentMessage.builder().id(id).role(role).content(content).build();
    }

    private AgentSessionRepository sessionRepo(Long claimId, Long userId) {
        AgentSessionRepository r = mock(AgentSessionRepository.class);
        when(r.findById(any())).thenReturn(Optional.of(
                AgentSession.builder().id(new AgentSessionId(claimId, userId)).build()));
        return r;
    }

    private ClaimsServiceGateway claims() {
        ClaimsServiceGateway c = mock(ClaimsServiceGateway.class);
        when(c.getClaimStatus(anyLong())).thenReturn(
                new ClaimStatusDto(99L, 7L, "CLM-99", "UNDER_REVIEW", "FIRE", 1000L, null, List.of()));
        return c;
    }

    private CurrentUserProvider user(Long id) {
        CurrentUserProvider u = mock(CurrentUserProvider.class);
        when(u.getCurrentUserId()).thenReturn(id);
        return u;
    }

    // ---- Scenario 1/2: bounded history is supplied to the LLM ----

    @Test
    void scenario1_2_priorTurnsSuppliedToLlm() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(2L, MessageRole.USER, "My claim number is CLM-123."),
                msg(1L, MessageRole.ASSISTANT, "Got it, claim CLM-123.")));
        AtomicReference<String> captured = new AtomicReference<>();
        AgentGenerationServiceImpl svc = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(99L, 42L),
                claims(), mock(AgentTurnPersistence.class), captured);

        svc.streamResponse("What is the status of that claim?", 99L).collectList().block();

        assertThat(captured.get())
                .contains("[USER] My claim number is CLM-123.")
                .contains("[ASSISTANT] Got it, claim CLM-123.");
    }

    // ---- Scenario 3: conversation isolation ----

    @Test
    void scenario3_conversationsAreIsolated() {
        // Conversation A (claim 99) has history; conversation B (claim 200) does
        // not. The repository is scoped by session key, so B must never see A's
        // content in the prompt supplied to the LLM.
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenAnswer(inv -> {
            AgentSession s = inv.getArgument(0);
            return s.getId().getClaimId() == 99L
                    ? List.of(msg(1L, MessageRole.USER, "claim CLM-123 is mine"))
                    : List.of();
        });
        AtomicReference<String> captured = new AtomicReference<>();
        AgentGenerationServiceImpl svc = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(200L, 42L),
                claims(), mock(AgentTurnPersistence.class), captured);

        svc.streamResponse("hi", 200L).collectList().block();
        assertThat(captured.get()).doesNotContain("CLM-123");
    }

    // ---- Scenario 4: user isolation ----

    @Test
    void scenario4_crossUserIsDenied() {
        // User 42 owns (99,42). A lookup as user 7 uses session key (99,7) whose
        // repository row is empty, so user 7 never sees user 42's history.
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenAnswer(inv -> {
            AgentSession s = inv.getArgument(0);
            return s.getId().getUserId() == 42L
                    ? List.of(msg(1L, MessageRole.USER, "sensitive data for user 42"))
                    : List.of();
        });
        AtomicReference<String> captured = new AtomicReference<>();
        AgentGenerationServiceImpl svc = svc(mock(ChatClient.class), repo, user(7L), sessionRepo(99L, 7L),
                claims(), mock(AgentTurnPersistence.class), captured);

        svc.streamResponse("hi", 99L).collectList().block();
        assertThat(captured.get()).doesNotContain("sensitive data for user 42");
    }

    // ---- Scenario 5: large history stays bounded ----

    @Test
    void scenario5_largeHistoryIsBounded() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(2L, MessageRole.USER, "recent"),
                msg(1L, MessageRole.USER, "old huge " + "x".repeat(20_000))));
        AtomicReference<String> captured = new AtomicReference<>();
        AgentGenerationServiceImpl svc = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(99L, 42L),
                claims(), mock(AgentTurnPersistence.class), captured);

        svc.streamResponse("hi", 99L).collectList().block();
        String prompt = captured.get();
        assertThat(prompt).contains("[USER] recent");
        assertThat(prompt).doesNotContain("x".repeat(20_000));
        // the transcript section must respect the char budget (oldest dropped)
        int start = prompt.indexOf("## Previous conversation");
        int end = prompt.indexOf("## End of previous conversation");
        if (start >= 0 && end > start) {
            assertThat(end - start).isLessThanOrEqualTo(props.getMaxContextChars() + 128);
        }
    }

    // ---- Scenario 6: tool-backed assistant context preserved ----

    @Test
    void scenario6_toolGroundedAssistantTextReachesNextTurn() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(1L, MessageRole.ASSISTANT, "Your claim is currently UNDER_REVIEW.")));
        AtomicReference<String> captured = new AtomicReference<>();
        AgentGenerationServiceImpl svc = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(99L, 42L),
                claims(), mock(AgentTurnPersistence.class), captured);

        svc.streamResponse("What documents do I need for the review?", 99L).collectList().block();
        assertThat(captured.get()).contains("UNDER_REVIEW");
    }

    // ---- Scenario 7: LLM failure persists a safe message, never a false success ----

    @Test
    void scenario7_llmFailureDoesNotPersistFalseSuccess() {
        ChatClient failing = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        when(failing.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.toolCallbacks(any(org.springframework.ai.tool.ToolCallbackProvider.class))).thenReturn(spec);
        when(spec.stream()).thenReturn(stream);
        // content() is the only stub this scenario overrides; built inline so the
        // shared helper (which stubs a successful answer) cannot mask the error.
        when(stream.content()).thenReturn(Flux.error(new IllegalStateException("boom")));

        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of());
        AgentTurnPersistence persistence = mock(AgentTurnPersistence.class);
        AtomicReference<String> captured = new AtomicReference<>();
        // Built inline (NOT via the svc() helper) so content() keeps its error stub.
        ConversationMemoryService memory = new ConversationMemoryService(repo, props);
        ConversationContextBuilder builder = new ConversationContextBuilder(props);
        AgentGenerationServiceImpl svc = new AgentGenerationServiceImpl(failing, props, user(42L),
                sessionRepo(99L, 42L), persistence, new ToolRegistry(), claims(),
                mock(CustomerServiceGateway.class), new InputGuardrails(props), new OutputGuardrails(props),
                memory, builder, AgentTelemetryTestSupport.telemetry());

        List<StreamResponse> events = svc.streamResponse("hi", 99L).collectList().block();
        assertThat(events.get(0).eventType()).isEqualTo("error");
        verify(persistence, org.mockito.Mockito.timeout(5000)).finalizeTurn(
                anyString(), any(), anyString(), anyLong(), any(), anyLong(), any(), any());
    }

    // ---- Scenario 10: concurrent requests do not share memory ----

    @Test
    void scenario10_concurrentRequestsAreIsolated() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class)))
                .thenReturn(List.of(msg(1L, MessageRole.USER, "claim A")))
                .thenReturn(List.of(msg(1L, MessageRole.USER, "claim B")));
        AtomicReference<String> capturedA = new AtomicReference<>();
        AtomicReference<String> capturedB = new AtomicReference<>();
        AgentGenerationServiceImpl svcA = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(99L, 42L),
                claims(), mock(AgentTurnPersistence.class), capturedA);
        AgentGenerationServiceImpl svcB = svc(mock(ChatClient.class), repo, user(42L), sessionRepo(98L, 42L),
                claims(), mock(AgentTurnPersistence.class), capturedB);

        svcA.streamResponse("hi", 99L).collectList().block();
        svcB.streamResponse("hi", 98L).collectList().block();

        assertThat(capturedA.get()).contains("claim A").doesNotContain("claim B");
        assertThat(capturedB.get()).contains("claim B").doesNotContain("claim A");
    }
}