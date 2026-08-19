package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.ai.AiErrorResolver;
import com.claimassist.platform.agent_service.ai.AiErrorResolver.Resolved;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionGuard;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionMetadata;
import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools.ProposedUpdate;
import com.claimassist.platform.agent_service.llm.PromptUtils;
import com.claimassist.platform.agent_service.memory.ConversationContextBuilder;
import com.claimassist.platform.agent_service.memory.ConversationMessage;
import com.claimassist.platform.agent_service.memory.ConversationMemoryService;
import com.claimassist.platform.agent_service.observability.AgentErrorCategory;
import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import com.claimassist.platform.agent_service.observability.SafeMetadata;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.security.InputGuardrails;
import com.claimassist.platform.agent_service.security.OutputGuardrails;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.AgentTurnPersistence;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CQRS COMMAND side of the agent conversation - every call here changes state
 * (creates sessions, persists turns, and - via AgentTurnPersistenceService -
 * enqueues claim-update saga requests). Read-only history lives on
 * AgentQueryServiceImpl.
 * <p>
 * This is a REAL Spring AI integration: the request streams actual model
 * tokens over SSE and, when the model chooses, executes the existing
 * {@link InsuranceAgentTools} through Spring AI's tool-calling mechanism. Tool
 * calls are bounded by a per-request {@link ToolExecutionGuard}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentGenerationServiceImpl implements AgentGenerationService {

    private static final String UNAVAILABLE_MESSAGE = "The assistant is currently unavailable. Please try again shortly.";

    private final ChatClient chatClient;
    private final AgentAiProperties agentAiProperties;
    private final CurrentUserProvider currentUserProvider;
    private final AgentSessionRepository agentSessionRepository;
    private final AgentTurnPersistence agentTurnPersistenceService;
    private final ToolRegistry toolRegistry;
    private final ClaimsServiceGateway claimsServiceGateway;
    private final CustomerServiceGateway customerServiceGateway;
    private final InputGuardrails inputGuardrails;
    private final OutputGuardrails outputGuardrails;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationContextBuilder conversationContextBuilder;
    private final AgentTelemetry agentTelemetry;

    @Override
    @PreAuthorize("@security.canAccessClaim(#claimId)")
    public Flux<StreamResponse> streamResponse(String userMessage, Long claimId) {
        long start = System.nanoTime();
        Long userId = currentUserProvider.getCurrentUserId();
        String requestId = UUID.randomUUID().toString();

        String correlation = "claim:" + claimId + ":user:" + userId + ":req:" + requestId;
        MDCUtility.putCorrelationId(correlation);

        String provider = "ollama";
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "ollama");
        agentTelemetry.requestStarted(requestId, correlation, claimId, userId, model, provider);

        // Fail-closed input guardrail: reject unsafe/oversized input before the
        // LLM is ever invoked. No session is created and no model call happens.
        InputGuardrails.Verdict verdict = inputGuardrails.check(userMessage);
        if (!verdict.allowed()) {
            log.info("Agent request {} rejected by input guardrail: code={}", requestId, verdict.errorCode());
            agentTelemetry.guardrailRejected(requestId, correlation, claimId, userId,
                    verdict.errorCode(), verdict.message());
            agentTelemetry.responseFailed(requestId, correlation, claimId, userId, 0L,
                    AgentErrorCategory.PROMPT_GUARDRAIL_REJECTION);
            return Flux.just(StreamResponse.error(requestId, verdict.errorCode(), verdict.message()))
                    .doFinally(signal -> MDCUtility.clearAll());
        }

        AgentSession session = createSessionIfNotExists(claimId, userId);
        ClaimStatusDto statusDto = claimsServiceGateway.getClaimStatus(claimId);
        Long policyId = statusDto == null ? null : statusDto.policyId();
        // A grounded fact the request already holds (from claims-service, not the LLM),
        // used by the output guardrail to catch hallucinated hard outcomes.
        String groundedStatus = statusDto == null ? null : statusDto.status();

        // Conversation memory (Phase 4): load the bounded, recent history for
        // THIS conversation (scoped to the authenticated user via the session
        // key) and fold it into the system prompt so the model can resolve
        // references ("that claim", "the status you mentioned") across turns.
        // Ownership is enforced by the composite session key (claimId, userId)
        // where userId always comes from the authenticated JWT - never from the
        // client - so a forged conversationId cannot reach another user's data.
        long contextStart = System.nanoTime();
        List<ConversationMessage> history = conversationMemoryService.loadRecent(session);
        ConversationContextBuilder.Context context =
                conversationContextBuilder.build(PromptUtils.INSURANCE_AGENT_SYSTEM_PROMPT, userMessage, history);
        agentTelemetry.contextBuilt(requestId, correlation, claimId, userId,
                durationMs(contextStart), history.size());

        List<ProposedUpdate> proposedUpdates = new CopyOnWriteArrayList<>();
        List<ToolExecutionMetadata> executions = new CopyOnWriteArrayList<>();
        InsuranceAgentTools tools = new InsuranceAgentTools(
                claimId, policyId, userId, claimsServiceGateway, customerServiceGateway, toolRegistry,
                agentAiProperties.getMaxNoteLength(), proposedUpdates::add,
                agentTelemetry, requestId, correlation);

        ToolCallbackProvider guardedProvider =
                new ToolExecutionGuard(agentAiProperties, toolRegistry, requestId, correlation, executions::add,
                        agentTelemetry, claimId, userId, SafeMetadata.hash(claimId))
                        .guarded(MethodToolCallbackProvider.builder().toolObjects(tools).build());

        StringBuilder fullText = new StringBuilder();

        long llmStart = System.nanoTime();
        agentTelemetry.streamStarted(requestId, correlation, claimId, userId);
        agentTelemetry.llmStarted(requestId, correlation, claimId, userId, model, provider);

        Flux<StreamResponse> events = chatClient.prompt()
                .system(context.systemPrompt())
                .user(context.userMessage())
                .toolCallbacks(guardedProvider)
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .map(chunk -> {
                    synchronized (fullText) {
                        fullText.append(chunk);
                    }
                    return StreamResponse.message(chunk, requestId);
                })
                .concatWith(Flux.defer(() -> {
                    List<StreamResponse> tail = new java.util.ArrayList<>(2);
                    String modelText = fullText.toString();

                    // Output guardrail: if the model asserted a hard outcome the
                    // grounded claim status does not support, append a controlled
                    // advisory before the terminal event. The model text itself is
                    // never rewritten.
                    String advisory = outputGuardrails
                            .advisory(modelText, groundedStatus == null ? List.of() : List.of(groundedStatus))
                            .orElse(null);
                    String persistedText = modelText;
                    if (advisory != null) {
                        tail.add(StreamResponse.message(advisory, requestId));
                        persistedText = modelText + "\n\n" + advisory;
                    }

                    agentTelemetry.llmCompleted(requestId, correlation, claimId, userId,
                            durationMs(llmStart), "COMPLETE", executions.size(), null, null);
                    agentTelemetry.streamCompleted(requestId, correlation, claimId, userId);
                    persistTurn(userMessage, session, persistedText, userId,
                            proposedUpdates, executions, durationSeconds(start), correlation, requestId);
                    agentTelemetry.responseCompleted(requestId, correlation, claimId, userId, durationMs(start));
                    tail.add(StreamResponse.done(requestId));
                    return Flux.fromIterable(tail);
                }));

        return events
                .timeout(Duration.ofMillis(agentAiProperties.getAgentTimeoutMs()))
                .onErrorResume(error -> {
                    Resolved resolved = AiErrorResolver.resolve(error);
                    AgentErrorCategory category = AgentErrorCategory.fromCode(resolved.code());
                    log.warn("Agent stream failed for request {}: code={} cause={}",
                            requestId, resolved.code(), error.toString());
                    agentTelemetry.llmFailed(requestId, correlation, claimId, userId,
                            durationMs(llmStart), category);
                    agentTelemetry.streamFailed(requestId, correlation, claimId, userId, category);
                    persistTurn(userMessage, session, resolved.message(), userId,
                            proposedUpdates, executions, durationSeconds(start), correlation, requestId);
                    agentTelemetry.responseFailed(requestId, correlation, claimId, userId,
                            durationMs(start), category);
                    return Flux.just(StreamResponse.error(requestId, resolved.code(), resolved.message()));
                })
                .doOnCancel(() -> {
                    agentTelemetry.streamCancelled(requestId, correlation, claimId, userId);
                    log.info("Agent stream cancelled for request {}", requestId);
                })
                .doFinally(signal -> {
                    if (!executions.isEmpty()) {
                        log.info("Request {} executed {} tool call(s): {}",
                                requestId, executions.size(), executions);
                    }
                    MDCUtility.clearAll();
                });
    }

    private void persistTurn(String userMessage, AgentSession session, String fullText, Long userId,
                         List<ProposedUpdate> proposedUpdates, List<ToolExecutionMetadata> executions,
                         long durationSeconds, String correlation, String requestId) {
        String text = (fullText == null || fullText.isBlank()) ? UNAVAILABLE_MESSAGE : fullText;
        Long conversationId = session.getId().getClaimId();
        try {
            MDCUtility.putCorrelationId(correlation);
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    agentTurnPersistenceService.finalizeTurn(
                            userMessage, session, text, durationSeconds,
                            null, userId, List.copyOf(proposedUpdates), List.copyOf(executions));
                } catch (RuntimeException persistenceFailure) {
                    // Phase 7.12 / 7.21: the user has already received their
                    // response (persistence is fire-and-forget), so we never
                    // corrupt state or report a false success. We record the
                    // failure via telemetry and a controlled log so the data
                    // loss is observable, and leave the DB/outbox untouched.
                    log.error("Agent turn persistence failed for request {}: {}",
                            requestId, persistenceFailure.toString());
                    agentTelemetry.persistenceFailed(requestId, correlation, conversationId, userId);
                }
            });
        } finally {
            MDCUtility.clearAll();
        }
    }

    private long durationSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000L;
    }

    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private AgentSession createSessionIfNotExists(Long claimId, Long userId) {
        AgentSessionId id = new AgentSessionId(claimId, userId);
        return agentSessionRepository.findById(id)
                .orElseGet(() -> {
                    // Two concurrent first requests for the same (claim, user)
                    // can race on the unique composite key. A duplicate-key save
                    // is recovered by re-reading the now-present row so the
                    // conversation state stays consistent.
                    try {
                        return agentSessionRepository.save(AgentSession.builder().id(id).build());
                    } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
                        return agentSessionRepository.findById(id)
                                .orElseThrow(() -> duplicate);
                    }
                });
    }
}