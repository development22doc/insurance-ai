package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.entity.AgentEvent;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools.ProposedUpdate;
import com.claimassist.platform.agent_service.repository.AgentEventRepository;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.enums.AgentEventType;
import com.claimassist.platform.common_lib.enums.MessageRole;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.event.ClaimUpdateRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Usage metadata from spring-ai is optional; avoid compile-time dependency
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists the outcome of one completed agent turn.
 * <p>
 * A SEPARATE Spring bean from AgentGenerationServiceImpl on purpose: it's
 * invoked from inside a Schedulers.boundedElastic() callback (a different
 * thread than the original request), so if this logic were just a method on
 * the same class, the call would be a plain self-invocation that bypasses
 * Spring's @Transactional proxy entirely - see the Lovable-clone platform's
 * ChatTurnPersistenceService for the full explanation of this well-known
 * Spring AOP pitfall. Going through a distinct injected bean means the
 * transaction boundary here is real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTurnPersistenceService {

    private static final String CLAIM_UPDATE_REQUEST_TOPIC = "claim-update-request-event";

    private final AgentMessageRepository agentMessageRepository;
    private final AgentEventRepository agentEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void finalizeTurn(String userMessage, AgentSession session, String fullText, long durationSeconds,
                              Object usage, Long userId, List<ProposedUpdate> proposedUpdates) {

        // If a concrete Usage object from spring-ai is provided at runtime, attempt to extract tokens
        int promptTokens = 0;
        int completionTokens = 0;
        if (usage != null) {
            try {
                java.lang.reflect.Method m1 = usage.getClass().getMethod("getPromptTokens");
                java.lang.reflect.Method m2 = usage.getClass().getMethod("getCompletionTokens");
                Object p = m1.invoke(usage);
                Object c = m2.invoke(usage);
                promptTokens = p instanceof Number ? ((Number) p).intValue() : 0;
                completionTokens = c instanceof Number ? ((Number) c).intValue() : 0;
            } catch (Exception e) {
                // ignore - fallback to zero
            }
        }

        agentMessageRepository.save(AgentMessage.builder()
                .agentSession(session)
                .role(MessageRole.USER)
                .content(userMessage)
                .tokensUsed(promptTokens)
                .build());

        AgentMessage assistantMessage = agentMessageRepository.save(AgentMessage.builder()
                .agentSession(session)
                .role(MessageRole.ASSISTANT)
                .content(fullText)
                .tokensUsed(completionTokens)
                .build());

        List<AgentEvent> events = new ArrayList<>();
        events.add(AgentEvent.builder()
                .agentMessage(assistantMessage)
                .type(AgentEventType.THOUGHT)
                .status(AgentEventStatus.CONFIRMED)
                .sequenceOrder(0)
                .content("Thought for " + durationSeconds + "s")
                .build());

        events.add(AgentEvent.builder()
                .agentMessage(assistantMessage)
                .type(AgentEventType.MESSAGE)
                .status(AgentEventStatus.CONFIRMED)
                .sequenceOrder(1)
                .content(fullText)
                .build());

        int seq = 2;
        for (ProposedUpdate update : proposedUpdates) {
            String sagaId = UUID.randomUUID().toString();

            AgentEvent proposalEvent = AgentEvent.builder()
                    .agentMessage(assistantMessage)
                    .type(AgentEventType.CLAIM_UPDATE_PROPOSED)
                    .status(AgentEventStatus.PENDING)
                    .sequenceOrder(seq++)
                    .content(update.note())
                    .sagaId(sagaId)
                    .proposedStatus(update.proposedStatus())
                    .build();
            events.add(proposalEvent);

            enqueueClaimUpdateRequest(session.getId().getClaimId(), sagaId, update, userId);
        }

        agentEventRepository.saveAll(events);
    }

    private void enqueueClaimUpdateRequest(Long claimId, String sagaId, ProposedUpdate update, Long userId) {
        if (outboxEventRepository.findFirstByAggregateIdAndEventType(sagaId, "ClaimUpdateRequestEvent").isPresent()) {
            log.info("Outbox: claim-update request for saga {} is already queued", sagaId);
            return;
        }

        ClaimUpdateRequestEvent event = new ClaimUpdateRequestEvent(
                claimId, sagaId, update.proposedStatus(), update.note(), userId);

        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(sagaId)
                    .eventType("ClaimUpdateRequestEvent")
                    .topic(CLAIM_UPDATE_REQUEST_TOPIC)
                    .partitionKey("claim-" + claimId)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Outbox: queued claim-update request for saga {} (claim {} -> {})", sagaId, claimId, update.proposedStatus());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ClaimUpdateRequestEvent for saga " + sagaId, e);
        }
    }
}
