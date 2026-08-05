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
// spring-ai ChatClient and Usage are optional; avoid compile-time dependency so service can start locally
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.claimassist.platform.agent_service.ai.OptionalChatClient chatClient;
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
        AtomicReference<Object> usageRef = new AtomicReference<>();

        // If an OptionalChatClient adapter is available, use it to invoke the model.
        if (chatClient != null) {
            try {
                String result = chatClient.invokeSimple(userMessage, tools);
                return Flux.just(new StreamResponse(result == null ? "LLM invoked." : result));
            } catch (Exception e) {
                log.warn("Failed to invoke OptionalChatClient, falling back to local stub: {}", e.toString());
            }
        }

        // Local fallback when no ChatClient is available: persist a minimal assistant message and return a stub response.
        Schedulers.boundedElastic().schedule(() ->
                agentTurnPersistenceService.finalizeTurn(
                        userMessage, session, "LLM disabled locally.", 0L,
                        null, userId, List.copyOf(proposedUpdates)));

        return Flux.just(new StreamResponse("LLM disabled locally."));
    }

    private AgentSession createSessionIfNotExists(Long claimId, Long userId) {
        AgentSessionId id = new AgentSessionId(claimId, userId);
        return agentSessionRepository.findById(id)
                .orElseGet(() -> agentSessionRepository.save(AgentSession.builder().id(id).build()));
    }
}
