package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools.ProposedUpdate;
import com.claimassist.platform.agent_service.llm.PromptUtils;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CQRS COMMAND side of the agent conversation - every call here changes
 * state (creates sessions, persists turns, and - via
 * {@link AgentTurnPersistenceService} - enqueues claim-update saga requests).
 * Read-only history lives on {@code AgentQueryServiceImpl}. Same streaming +
 * outbox shape as AiGenerationServiceImpl in the Lovable-clone platform.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentGenerationServiceImpl implements AgentGenerationService {

    private final ChatClient chatClient;
    private final CurrentUserProvider currentUserProvider;
    private final AgentSessionRepository agentSessionRepository;
    private final AgentTurnPersistenceService agentTurnPersistenceService;
    private final ClaimsServiceGateway claimsServiceGateway;
    private final CustomerServiceGateway customerServiceGateway;

    @Override
    @PreAuthorize("@security.canAccessClaim(#claimId)")
    public Flux<StreamResponse> streamResponse(String userMessage, Long claimId) {

        Long userId = currentUserProvider.getCurrentUserId();
        AgentSession session = createSessionIfNotExists(claimId, userId);

        // Resolve the policy behind this claim ONCE per turn, so the
        // get_policy_coverage tool doesn't need to make an extra round-trip to
        // claims-service just to find out which policy it's even asking about.
        Long policyId = claimsServiceGateway.getClaimStatus(claimId).policyId();

        List<ProposedUpdate> proposedUpdates = new CopyOnWriteArrayList<>();

        InsuranceAgentTools tools = new InsuranceAgentTools(
                claimId, policyId, claimsServiceGateway, customerServiceGateway, proposedUpdates::add);

        StringBuilder fullResponseBuffer = new StringBuilder();
        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(0L);
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        return chatClient.prompt()
                .system(PromptUtils.INSURANCE_AGENT_SYSTEM_PROMPT)
                .user(userMessage)
                .tools(tools)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    String content = response.getResult().getOutput().getText();

                    if (content != null && !content.isEmpty() && endTime.get() == 0) {
                        endTime.set(System.currentTimeMillis());
                    }
                    if (response.getMetadata().getUsage() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                    fullResponseBuffer.append(content);
                })
                .doOnComplete(() -> {
                    long duration = (endTime.get() - startTime.get()) / 1000;
                    // Offload the (blocking JDBC) persistence + saga-enqueue work so it
                    // never runs on - and blocks - Reactor's non-blocking event loop.
                    Schedulers.boundedElastic().schedule(() ->
                            agentTurnPersistenceService.finalizeTurn(
                                    userMessage, session, fullResponseBuffer.toString(), duration,
                                    usageRef.get(), userId, List.copyOf(proposedUpdates)));
                })
                .doOnError(error -> log.error("Error during agent streaming for claimId: {}", claimId, error))
                .map(response -> {
                    String text = response.getResult().getOutput().getText();
                    return new StreamResponse(text != null ? text : "");
                });
    }

    private AgentSession createSessionIfNotExists(Long claimId, Long userId) {
        AgentSessionId id = new AgentSessionId(claimId, userId);
        return agentSessionRepository.findById(id)
                .orElseGet(() -> agentSessionRepository.save(AgentSession.builder().id(id).build()));
    }
}
