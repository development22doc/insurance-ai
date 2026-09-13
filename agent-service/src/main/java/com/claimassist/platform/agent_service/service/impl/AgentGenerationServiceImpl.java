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
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGatewayApi;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGatewayApi;
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
@Slf4j
public class AgentGenerationServiceImpl implements AgentGenerationService {

    private static final String UNAVAILABLE_MESSAGE = "The assistant is currently unavailable. Please try again shortly.";

    private final ChatClient chatClient;
    private final AgentAiProperties agentAiProperties;
    private final CurrentUserProvider currentUserProvider;
    private final AgentSessionRepository agentSessionRepository;
    private final AgentTurnPersistence agentTurnPersistenceService;
    private final ToolRegistry toolRegistry;
    private final ClaimsServiceGatewayApi claimsServiceGateway;
    private final CustomerServiceGatewayApi customerServiceGateway;
    private final InputGuardrails inputGuardrails;
    private final OutputGuardrails outputGuardrails;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationContextBuilder conversationContextBuilder;
    private final AgentTelemetry agentTelemetry;
    private final com.claimassist.platform.agent_service.security.SecurityExpressions security;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentGenerationServiceImpl(ChatClient chatClient,
                                     AgentAiProperties agentAiProperties,
                                     CurrentUserProvider currentUserProvider,
                                     AgentSessionRepository agentSessionRepository,
                                     AgentTurnPersistence agentTurnPersistenceService,
                                     ToolRegistry toolRegistry,
                                     ClaimsServiceGatewayApi claimsServiceGateway,
                                     CustomerServiceGatewayApi customerServiceGateway,
                                     InputGuardrails inputGuardrails,
                                     OutputGuardrails outputGuardrails,
                                     ConversationMemoryService conversationMemoryService,
                                     ConversationContextBuilder conversationContextBuilder,
                                     AgentTelemetry agentTelemetry,
                                     com.claimassist.platform.agent_service.security.SecurityExpressions security) {
        this.chatClient = chatClient;
        this.agentAiProperties = agentAiProperties;
        this.currentUserProvider = currentUserProvider;
        this.agentSessionRepository = agentSessionRepository;
        this.agentTurnPersistenceService = agentTurnPersistenceService;
        this.toolRegistry = toolRegistry;
        this.claimsServiceGateway = claimsServiceGateway;
        this.customerServiceGateway = customerServiceGateway;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.conversationMemoryService = conversationMemoryService;
        this.conversationContextBuilder = conversationContextBuilder;
        this.agentTelemetry = agentTelemetry;
        this.security = security;
    }

    public AgentGenerationServiceImpl(ChatClient chatClient,
                                     AgentAiProperties agentAiProperties,
                                     CurrentUserProvider currentUserProvider,
                                     AgentSessionRepository agentSessionRepository,
                                     AgentTurnPersistence agentTurnPersistenceService,
                                     ToolRegistry toolRegistry,
                                     ClaimsServiceGatewayApi claimsServiceGateway,
                                     CustomerServiceGatewayApi customerServiceGateway,
                                     InputGuardrails inputGuardrails,
                                     OutputGuardrails outputGuardrails,
                                     ConversationMemoryService conversationMemoryService,
                                     ConversationContextBuilder conversationContextBuilder,
                                     AgentTelemetry agentTelemetry) {
        this(chatClient, agentAiProperties, currentUserProvider, agentSessionRepository,
                agentTurnPersistenceService, toolRegistry, claimsServiceGateway,
                customerServiceGateway, inputGuardrails, outputGuardrails,
                conversationMemoryService, conversationContextBuilder, agentTelemetry,
                new com.claimassist.platform.agent_service.security.SecurityExpressions(claimsServiceGateway));
    }

    @Override
    public Flux<StreamResponse> streamResponse(String userMessage, Long claimId) {
        // Reactive entry: obtain SecurityContext and perform the permission check
        var flux = org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(reactor.core.publisher.Mono.just(org.springframework.security.core.context.SecurityContextHolder.createEmptyContext()))
                .flatMapMany(securityContext -> {
                    var auth = securityContext.getAuthentication();

                    // Extract Jwt (if present) without blocking. If no auth is on the
                    // request, we still allow the downstream claims-service RBAC check to
                    // decide (fail-closed on unauthorized responses), which keeps the
                    // authorization boundary in the service layer without making the
                    // public agent stream unusable in test or local non-authenticated runs.
                    final org.springframework.security.oauth2.jwt.Jwt jwtLocal;
                    if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt pJwt) {
                        jwtLocal = pJwt;
                    } else if (auth != null && auth.getCredentials() instanceof org.springframework.security.oauth2.jwt.Jwt cJwt) {
                        jwtLocal = cJwt;
                    } else {
                        jwtLocal = null;
                    }

                    // Reactive permission check via SecurityExpressions; if no
                    // SecurityContext is available, the expression will call the claims
                    // service without a bearer token and rely on the service-side RBAC.
                    reactor.core.publisher.Mono<Boolean> allowed = security.canAccessClaim(claimId);

                    return allowed.flatMapMany(isAllowed -> {
                        if (!isAllowed) {
                            return reactor.core.publisher.Flux.error(
                                    new org.springframework.security.access.AccessDeniedException("Forbidden"));
                        }

                        final long start = System.nanoTime();
                        final String requestId = UUID.randomUUID().toString();

                        // Extract Jwt (if present) without blocking
                        final org.springframework.security.oauth2.jwt.Jwt jwtInner = jwtLocal;

                        // Extract JWT token string for tool execution
                        final String jwtTokenString = jwtInner != null ? jwtInner.getTokenValue() : null;

                        // Safe JWT diagnostics - NEVER log the token itself
                        try {
                            if (jwtInner != null) {
                                boolean subPresent = jwtInner.getClaim("sub") != null;
                                boolean preferredUsernamePresent = jwtInner.getClaim("preferred_username") != null;
                                boolean emailPresent = jwtInner.getClaim("email") != null;
                                boolean userIdPresent = jwtInner.getClaim("userId") != null;
                                String azp = jwtInner.getClaimAsString("azp");
                                String clientId = jwtInner.getClaimAsString("client_id");
                                boolean isServiceToken = (azp != null && !azp.isBlank()) || (clientId != null && !clientId.isBlank());

                                log.info("JWT DIAGNOSTICS [requestId={}]: token_present=true, token_type={}, sub_present={}, preferred_username_present={}, email_present={}, userId_present={}, azp_present={}, client_id_present={}",
                                        requestId,
                                        isServiceToken ? "SERVICE" : "USER",
                                        subPresent,
                                        preferredUsernamePresent,
                                        emailPresent,
                                        userIdPresent,
                                        azp != null && !azp.isBlank(),
                                        clientId != null && !clientId.isBlank());

                                if (!userIdPresent && !isServiceToken) {
                                    log.error("JWT DIAGNOSTICS [requestId={}]: CRITICAL - USER token missing userId claim", requestId);
                                }
                            } else {
                                log.info("JWT DIAGNOSTICS [requestId={}]: token_present=false", requestId);
                            }
                        } catch (Exception e) {
                            log.error("JWT DIAGNOSTICS [requestId={}]: Failed to read JWT: {}", requestId, e.toString());
                        }

                        final Long userId;
                        if (jwtInner != null && jwtInner.getClaim("userId") != null) {
                            Object userIdObj = jwtInner.getClaim("userId");
                            if (userIdObj instanceof Number number) {
                                userId = number.longValue();
                            } else {
                                userId = Long.valueOf(userIdObj.toString());
                            }
                        } else {
                            userId = null;
                        }

                        final String correlation = "claim:" + claimId + ":user:" + userId + ":req:" + requestId;
                        MDCUtility.putCorrelationId(correlation);

                        final String provider = "ollama";
                        final String model = System.getenv().getOrDefault("OLLAMA_MODEL", "ollama");
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
                            return reactor.core.publisher.Flux.just(StreamResponse.error(requestId, verdict.errorCode(), verdict.message()))
                                    .doFinally(signal -> MDCUtility.clearAll());
                        }

                        // Create session on boundedElastic to avoid blocking reactor threads
                        return reactor.core.publisher.Mono.fromCallable(() -> createSessionIfNotExists(claimId, userId))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(session -> {
                                    // Fetch claim status reactively
                                    return claimsServiceGateway.getClaimStatusReactive(claimId)
                                            .switchIfEmpty(reactor.core.publisher.Mono.defer(() -> reactor.core.publisher.Mono.just(
                                                    new ClaimStatusDto(claimId, null, null, "UNKNOWN", null, null, null, java.util.List.of())
                                            )))
                                            .flatMapMany(statusDto -> {
                                                final Long policyId = statusDto == null ? null : statusDto.policyId();
                                                final String groundedStatus = statusDto == null ? null : statusDto.status();

                                                long contextStart = System.nanoTime();
                                                // Load recent history on boundedElastic because it uses JPA
                                                return reactor.core.publisher.Mono.fromCallable(() -> conversationMemoryService.loadRecent(session))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .flatMapMany(history -> {
                                                            final ConversationContextBuilder.Context context =
                                                                    conversationContextBuilder.build(PromptUtils.INSURANCE_AGENT_SYSTEM_PROMPT, userMessage, history);
                                                            agentTelemetry.contextBuilt(requestId, correlation, claimId, userId,
                                                                    durationMs(contextStart), history.size());

                                                            final List<ProposedUpdate> proposedUpdates = new CopyOnWriteArrayList<>();
                                                            final List<ToolExecutionMetadata> executions = new CopyOnWriteArrayList<>();
                                                            InsuranceAgentTools tools = new InsuranceAgentTools(
                                                                    claimId, policyId, userId, claimsServiceGateway, customerServiceGateway, toolRegistry,
                                                                    agentAiProperties.getMaxNoteLength(), proposedUpdates::add,
                                                                    agentTelemetry, requestId, correlation, jwtTokenString);

                                                            ToolCallbackProvider guardedProvider =
                                                                    new ToolExecutionGuard(agentAiProperties, toolRegistry, requestId, correlation, executions::add,
                                                                            agentTelemetry, claimId, userId, SafeMetadata.hash(claimId))
                                                                            .guarded(MethodToolCallbackProvider.builder().toolObjects(tools).build());

                                                            final StringBuilder fullText = new StringBuilder();

                                                            final long llmStart = System.nanoTime();
                                                            agentTelemetry.streamStarted(requestId, correlation, claimId, userId);
                                                            agentTelemetry.llmStarted(requestId, correlation, claimId, userId, model, provider);

                                                            log.info("LLM STREAM DIAGNOSTICS [requestId={}]: stream_initiated=true, model={}, provider={}",
                                                                    requestId, model, provider);

                                                            log.info("STREAM DIAGNOSTICS [requestId={}]: using_reactive_streaming", requestId);
                                                            var contentStream = chatClient.prompt()
                                                                    .system(context.systemPrompt())
                                                                    .user(context.userMessage())
                                                                    .toolCallbacks(guardedProvider)
                                                                    .stream()
                                                                    .content()
                                                                    .doOnSubscribe(sub -> log.info("STREAM DIAGNOSTICS [requestId={}]: reactive_flux_subscribed=true", requestId))
                                                                    .doOnNext(chunk -> log.info("STREAM DIAGNOSTICS [requestId={}]: reactive_chunk_received=true, length={}",
                                                                            requestId, chunk != null ? chunk.length() : 0))
                                                                    .doOnComplete(() -> log.info("STREAM DIAGNOSTICS [requestId={}]: reactive_flux_complete=true", requestId));

                                                            var filteredStream = contentStream
                                                                    .filter(chunk -> chunk != null && !chunk.isBlank());

                                                            var mappedStream = filteredStream
                                                                    .map(chunk -> {
                                                                        synchronized (fullText) {
                                                                            fullText.append(chunk);
                                                                        }
                                                                        return StreamResponse.message(chunk, requestId);
                                                                    });

                                                            Flux<StreamResponse> events = mappedStream
                                                                    .concatWith(reactor.core.publisher.Flux.defer(() -> {
                                                                        List<StreamResponse> tail = new java.util.ArrayList<>(2);
                                                                        String modelText = fullText.toString();

                                                                        String advisory = outputGuardrails
                                                                                .advisory(modelText, groundedStatus == null ? java.util.List.of() : java.util.List.of(groundedStatus))
                                                                                .orElse(null);
                                                                        String persistedText = modelText;
                                                                        if (advisory != null) {
                                                                            tail.add(StreamResponse.message(advisory, requestId));
                                                                            persistedText = modelText + "\n\n" + advisory;
                                                                        }

                                                                        agentTelemetry.llmCompleted(requestId, correlation, claimId, userId,
                                                                                durationMs(llmStart), "COMPLETE", executions.size(), null, null);
                                                                        agentTelemetry.streamCompleted(requestId, correlation, claimId, userId);
                                                                        persistTurn(userMessage, session, persistedText,
                                                                                userId, proposedUpdates, executions, durationSeconds(start), correlation, requestId);
                                                                        agentTelemetry.responseCompleted(requestId, correlation, claimId, userId, durationMs(start));
                                                                        tail.add(StreamResponse.done(requestId));
                                                                        return reactor.core.publisher.Flux.fromIterable(tail);
                                                                    }));

                                                            return events
                                                                    .timeout(java.time.Duration.ofMillis(agentAiProperties.getAgentTimeoutMs()))
                                                                    .onErrorResume(error -> {
                                                                        Resolved resolved = AiErrorResolver.resolve(error);
                                                                        AgentErrorCategory category = AgentErrorCategory.fromCode(resolved.code());

                                                                        log.warn("Agent stream failed for request {}: code={} cause={} errorType={}",
                                                                                requestId, resolved.code(), error.toString(), error.getClass().getSimpleName());

                                                                        Throwable rootCause = error;
                                                                        while (rootCause.getCause() != null) {
                                                                            rootCause = rootCause.getCause();
                                                                        }
                                                                        if (rootCause != error) {
                                                                            log.warn("Root cause for request {}: type={} message={}",
                                                                                    requestId, rootCause.getClass().getSimpleName(), rootCause.getMessage());
                                                                        }

                                                                        agentTelemetry.llmFailed(requestId, correlation, claimId, userId,
                                                                                durationMs(llmStart), category);
                                                                        agentTelemetry.streamFailed(requestId, correlation, claimId, userId, category);
                                                                        persistTurn(userMessage, session, resolved.message(), userId,
                                                                                proposedUpdates, executions, durationSeconds(start), correlation, requestId);
                                                                        agentTelemetry.responseFailed(requestId, correlation, claimId, userId,
                                                                                durationMs(start), category);
                                                                        return reactor.core.publisher.Flux.just(StreamResponse.error(requestId, resolved.code(), resolved.message()));
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
                                                        });
                                                });
                                });
                        });
                });
        return flux.switchIfEmpty(reactor.core.publisher.Flux.error(new org.springframework.security.access.AccessDeniedException("Unauthenticated")));
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
