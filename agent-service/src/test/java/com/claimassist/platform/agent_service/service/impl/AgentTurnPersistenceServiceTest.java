package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.ai.tool.ToolExecutionMetadata;
import com.claimassist.platform.agent_service.entity.AgentEvent;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools.ProposedUpdate;
import com.claimassist.platform.agent_service.repository.AgentEventRepository;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.enums.AgentEventType;
import com.claimassist.platform.common_lib.enums.MessageRole;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the new {@link AgentTurnPersistence} implementation - the
 * persistence + saga/outbox enqueue path that runs asynchronously after a turn.
 */
class AgentTurnPersistenceServiceTest {

    private AgentMessageRepository messageRepo;
    private AgentEventRepository eventRepo;
    private OutboxEventRepository outboxRepo;
    private AgentTurnPersistenceService service;

    private final AgentSession session =
            AgentSession.builder().id(new AgentSessionId(99L, 1L)).build();

    @BeforeEach
    void setUp() {
        messageRepo = mock(AgentMessageRepository.class);
        eventRepo = mock(AgentEventRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        service = new AgentTurnPersistenceService(
                messageRepo, eventRepo, outboxRepo, new ObjectMapper(),
                mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    @Test
    void persistsUserAndAssistantMessagesWithBaseEvents() {
        service.finalizeTurn("What is my status?", session, "Your claim is UNDER_REVIEW.",
                5L, null, 1L, List.of(), List.of());

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageRepo, times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).hasSize(2);
        AgentMessage userMsg = messageCaptor.getAllValues().stream()
                .filter(m -> m.getRole() == MessageRole.USER).findFirst().orElseThrow();
        AgentMessage assistantMsg = messageCaptor.getAllValues().stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT).findFirst().orElseThrow();
        assertThat(userMsg.getContent()).isEqualTo("What is my status?");
        assertThat(assistantMsg.getContent()).isEqualTo("Your claim is UNDER_REVIEW.");

        ArgumentCaptor<List<AgentEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepo).saveAll(eventsCaptor.capture());
        List<AgentEvent> events = eventsCaptor.getValue();
        assertThat(events).hasSize(2);
        assertThat(events).extracting(AgentEvent::getType)
                .containsExactly(AgentEventType.THOUGHT, AgentEventType.MESSAGE);
        assertThat(events).extracting(AgentEvent::getSequenceOrder)
.containsExactly(0, 1);
    }

    @Test
    void persistsBoundedToolLogEventsForExecutedTools() {
        service.finalizeTurn("u", session, "answer", 5L, null, 1L, List.of(),
                List.of(new ToolExecutionMetadata("r1", "c1", "get_claim_status", "SUCCESS", 3L),
                        new ToolExecutionMetadata("r1", "c1", "get_claim_documents", "FAILED", 9L)));

        ArgumentCaptor<List<AgentEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepo).saveAll(eventsCaptor.capture());
        List<AgentEvent> logs = eventsCaptor.getValue().stream()
                .filter(e -> e.getType() == AgentEventType.TOOL_LOG)
                .toList();
        // Compact, safe (name + outcome) only - never raw payloads or stack traces.
        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(AgentEvent::getContent)
                .containsExactly("get_claim_status -> SUCCESS", "get_claim_documents -> FAILED");
        assertThat(logs).allMatch(e -> e.getStatus() == AgentEventStatus.CONFIRMED);
    }

    @Test
    void noToolLogEventsWhenNoToolsRan() {
        service.finalizeTurn("u", session, "answer", 5L, null, 1L, List.of(), List.of());
        ArgumentCaptor<List<AgentEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepo).saveAll(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).noneMatch(e -> e.getType() == AgentEventType.TOOL_LOG);
    }

    @Test
    void queuesSagaAndOutboxEventForProposedUpdate() {
        when(outboxRepo.findFirstByAggregateIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.empty());

service.finalizeTurn("u", session, "answer", 5L, null, 1L,
                List.of(new ProposedUpdate("DOCS_REQUESTED", "photos too blurry")), List.of());

        // A CLAIM_UPDATE_PROPOSED agent event with a saga id is recorded.
        ArgumentCaptor<List<AgentEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepo).saveAll(eventsCaptor.capture());
        AgentEvent proposal = eventsCaptor.getValue().stream()
                .filter(e -> e.getType() == AgentEventType.CLAIM_UPDATE_PROPOSED)
                .findFirst().orElseThrow();
        assertThat(proposal.getProposedStatus()).isEqualTo("DOCS_REQUESTED");
        assertThat(proposal.getSagaId()).isNotBlank();

        // The outbox row is enqueued with the correct topic + structured payload.
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("claim-update-request-event");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAggregateId()).isEqualTo(proposal.getSagaId());
        assertThat(outbox.getPayload()).contains("DOCS_REQUESTED").contains("photos too blurry");
    }

    @Test
    void doesNotEnqueueDuplicateOutboxForAlreadyQueuedSaga() {
        when(outboxRepo.findFirstByAggregateIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.of(OutboxEvent.builder().build()));

service.finalizeTurn("u", session, "answer", 5L, null, 1L,
                List.of(new ProposedUpdate("DOCS_REQUESTED", "note")), List.of());

        verify(outboxRepo, never()).save(any(OutboxEvent.class));
    }

    @Test
    void extractsTokenCountsFromSpringAiUsageWhenPresent() {
        // Usage is introspected reflectively to avoid a hard spring-ai compile
        // dependency; simulate a compatible object.
        Object usage = new Object() {
            public int getPromptTokens() { return 12; }
            public int getCompletionTokens() { return 34; }
        };

        service.finalizeTurn("u", session, "answer", 5L, usage, 1L, List.of(), List.of());

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageRepo, times(2)).save(captor.capture());
        AgentMessage userMsg = captor.getAllValues().stream()
                .filter(m -> m.getRole() == MessageRole.USER).findFirst().orElseThrow();
        AgentMessage assistantMsg = captor.getAllValues().stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT).findFirst().orElseThrow();
        assertThat(userMsg.getTokensUsed()).isEqualTo(12);
        assertThat(assistantMsg.getTokensUsed()).isEqualTo(34);
    }

    @Test
    void serializationFailureThrowsControlledIllegalStateException() throws Exception {
        when(outboxRepo.findFirstByAggregateIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.empty());
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        AgentTurnPersistenceService failing = new AgentTurnPersistenceService(
                messageRepo, eventRepo, outboxRepo, brokenMapper,
                mock(EventLogger.class), mock(PerformanceLogger.class));

assertThatThrownBy(() -> failing.finalizeTurn("u", session, "answer", 5L, null, 1L,
                List.of(new ProposedUpdate("APPROVED", "note")), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize");
    }
}
