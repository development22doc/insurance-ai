package com.claimassist.platform.agent_service.ai.model;

import com.claimassist.platform.agent_service.ai.tool.QwenToolCall;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallParser;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallingManager;
import com.claimassist.platform.agent_service.ai.tool.ToolCallLoopTracker;
import com.claimassist.platform.agent_service.ai.tool.ToolPlanner;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * {@link OllamaChatModel} that adds support for streaming models (such as
 * qwen2.5-coder) which emit a structured tool call as JSON text inside the
 * assistant {@code content} instead of as a native {@code message.tool_calls}.
 * <p>
 * Native Spring AI tool calls are delegated verbatim to {@link #stream(Prompt)}
 * (via {@code super.stream}), which preserves the framework's internal tool
 * execution loop unchanged. For the Qwen text case, this override accumulates
 * the assistant content across streaming chunks, uses the strict
 * {@link QwenToolCallParser} to recognise a complete tool-call object, suppresses
 * that object from ever reaching the client as assistant text, and executes it
 * through the existing {@link QwenToolCallingManager} (which routes every call
 * through the same {@code GuardedToolCallback} / {@code ToolExecutionGuard}
 * / {@code InsuranceAgentTools} path as a native call). The tool result is fed
 * back to the model and streaming continues, including multiple sequential Qwen
 * tool calls, capped by {@code maxToolCalls}.
 * <p>
 * Only sufficient logic to enable cross-chunk accumulation is reimplemented
 * here; the per-turn model request is delegated to {@code super.stream(prompt)},
 * which reuses all of Spring AI's existing request building, observations,
 * retry and native tool execution.
 */
public class QwenOllamaChatModel extends OllamaChatModel {

    private static final Logger log = LoggerFactory.getLogger(QwenOllamaChatModel.class);

    /**
     * Pathological tool-calling loops are detected and aborted by the request-scoped
     * {@link ToolCallLoopTracker} rather than running until the {@code maxToolCalls}
     * hard limit. It terminates both a back-to-back identical call and an alternating
     * loop in which the model re-invokes a tool already completed this turn. Legitimate
     * sequences (e.g. get_claim_status then propose_claim_update) are never affected.
     */

    private final ToolCallingManager toolCallingManager;
    private final OllamaChatOptions defaultOptions;
    private final QwenToolCallParser parser;
    private final int maxToolCalls;

    public QwenOllamaChatModel(OllamaApi ollamaApi, OllamaChatOptions defaultOptions,
                               ToolCallingManager toolCallingManager,
                               ObservationRegistry observationRegistry,
                               ModelManagementOptions modelManagementOptions,
                               ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate,
                               RetryTemplate retryTemplate) {
        this(ollamaApi, defaultOptions, toolCallingManager, observationRegistry, modelManagementOptions,
                toolExecutionEligibilityPredicate, retryTemplate, 10);
    }

    public QwenOllamaChatModel(OllamaApi ollamaApi, OllamaChatOptions defaultOptions,
                               ToolCallingManager toolCallingManager,
                               ObservationRegistry observationRegistry,
                               ModelManagementOptions modelManagementOptions,
                               ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate,
                               RetryTemplate retryTemplate, int maxToolCalls) {
        super(ollamaApi, defaultOptions, toolCallingManager, observationRegistry, modelManagementOptions,
                toolExecutionEligibilityPredicate, retryTemplate);
        this.toolCallingManager = toolCallingManager;
        this.defaultOptions = defaultOptions;
        this.parser = new QwenToolCallParser();
        this.maxToolCalls = Math.max(1, maxToolCalls);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Prompt requestPrompt = mergeOptions(prompt);
        Set<String> registeredNames = registeredToolNames(requestPrompt);
        List<String> plan = ToolPlanner.plan(userText(requestPrompt), registeredNames);
        return streamWithToolExecution(requestPrompt, 0, new ToolCallLoopTracker(), new PlanState(plan));
    }

    /**
     * Mutable, request-scoped state for deterministic completion of the READ-only
     * tool plan for a multi-part request. Created once per stream (from the first
     * user message) and threaded through every {@link #streamWithToolExecution}
     * recursion so that deferred prose and executed-tool bookkeeping survive
     * across turns without ever reaching another request.
     */
    private static final class PlanState {
        final List<String> planned;
        final Set<String> executed = new HashSet<>();
        final StringBuilder heldProse = new StringBuilder();

        PlanState(List<String> planned) {
            this.planned = planned;
        }

        boolean complete() {
            return planned.isEmpty() || executed.containsAll(planned);
        }

        String nextPending() {
            for (String tool : planned) {
                if (!executed.contains(tool)) {
                    return tool;
                }
            }
            return null;
        }
    }

    /** The last user message in the prompt, used to derive the tool plan. */
    private static String userText(Prompt prompt) {
        List<Message> instructions = prompt.getInstructions();
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Message message = instructions.get(i);
            if (message instanceof UserMessage userMessage && StringUtils.hasText(userMessage.getText())) {
                return userMessage.getText();
            }
        }
        return "";
    }

    /**
     * Stream a single model turn, accumulating the assistant content so that a
     * Qwen text tool call spread across chunks can be detected, suppressed and
     * executed. Native tool calls are handled entirely inside {@code super.stream}
     * and are relayed unchanged.
     */
    private Flux<ChatResponse> streamWithToolExecution(Prompt requestPrompt, int round, ToolCallLoopTracker loopGuard,
                                                       PlanState planState) {
        // Once the request's read-tool plan is complete, any prose that had been
        // held back while completing the plan is flushed FIRST so nothing the model
        // already drafted is lost. This runs at the start of the first turn where
        // the plan is satisfied (heldProse is cleared after the single flush).
        Flux<ChatResponse> prefix = Flux.empty();
        if (planState.complete() && planState.heldProse.length() > 0) {
            String held = planState.heldProse.toString();
            planState.heldProse.setLength(0);
            prefix = Flux.just(ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage(held))))
                    .build());
        }

        AtomicReference<StringBuilder> accumulator = new AtomicReference<>(new StringBuilder());
        AtomicReference<List<ChatResponse>> buffer = new AtomicReference<>(new ArrayList<>());
        AtomicBoolean passthrough = new AtomicBoolean(false);
        final Set<String> registeredNames = registeredToolNames(requestPrompt);

        Flux<ChatResponse> source = super.stream(requestPrompt);

        // Sanitize the raw per-chunk stream: buffer while the accumulated content is
        // still a possible tool-call prefix, suppress it once a complete Qwen tool
        // call is detected, and otherwise switch to live pass-through (normal text).
        Flux<ChatResponse> sanitized = source.doOnSubscribe(s -> {
                    accumulator.get().setLength(0);
                    buffer.get().clear();
                    passthrough.set(false);
                })
                .concatMap(chunk -> {
                    String delta = textOf(chunk);
                    if (StringUtils.hasText(delta)) {
                        accumulator.get().append(delta);
                    }
                    QwenToolCallParser.Classification c = parser.classify(accumulator.get().toString(), registeredNames);
                    if (passthrough.get()) {
                        return Flux.just(chunk);
                    }
                    switch (c) {
                        case TOOL_CALL -> {
                            // Complete Qwen text tool call: suppress every buffered and
                            // current chunk; execution happens on completion below.
                            return Flux.empty();
                        }
                        case POSSIBLE_PREFIX -> {
                            if (StringUtils.hasText(delta)) {
                                buffer.get().add(chunk);
                            }
                            return Flux.empty();
                        }
                        default -> { // NOT_A_TOOL_CALL
                            // This content is normal assistant text (or a non-tool JSON
                            // value). If the request's read-tool plan is not yet satisfied,
                            // hold the prose and let the planner complete the missing tool(s)
                            // before anything reaches the client. Otherwise flush anything
                            // buffered and switch to live streaming.
                            if (!planState.complete()) {
                                if (StringUtils.hasText(delta)) {
                                    planState.heldProse.append(delta);
                                }
                                return Flux.empty();
                            }
                            passthrough.set(true);
                            List<ChatResponse> flushed = List.copyOf(buffer.get());
                            buffer.get().clear();
                            return Flux.concat(Flux.fromIterable(flushed), Flux.just(chunk));
                        }
                    }
                });

        Flux<ChatResponse> continuation = Flux.defer(() -> {
            String accumulated = accumulator.get().toString();
            if (parser.classify(accumulated, registeredNames) != QwenToolCallParser.Classification.TOOL_CALL) {
                // The turn ended without a complete model tool call.
                // Any still-buffered possible-prefix content belongs to the prose we are
                // about to either hold (plan incomplete) or emit (plan complete).
                for (ChatResponse heldChunk : List.copyOf(buffer.get())) {
                    String heldText = textOf(heldChunk);
                    if (StringUtils.hasText(heldText)) {
                        planState.heldProse.append(heldText);
                    }
                }
                buffer.get().clear();

                // Deterministic completion: if the request still needs read tools the
                // model did not emit, execute the next planned tool through the guarded
                // callback path and stream another turn, so the final answer is grounded
                // in ALL of the data the question asked for. Writes are never planned.
                String pendingTool = planState.nextPending();
                if (pendingTool != null && round < maxToolCalls
                        && toolCallingManager instanceof QwenToolCallingManager qwenManager) {
                    log.debug("Completing read-tool plan: executing planned tool '{}' (round {}).", pendingTool, round + 1);
                    planState.executed.add(pendingTool);
                    QwenToolCall plannedCall = new QwenToolCall(pendingTool, Map.of());
                    ToolExecutionResult plannedResult = qwenManager.executePlannedToolCall(requestPrompt, plannedCall);
                    return streamWithToolExecution(
                            new Prompt(plannedResult.conversationHistory(), requestPrompt.getOptions()),
                            round + 1, loopGuard, planState);
                }

                // Plan complete (or planner unavailable / budget exhausted): emit any
                // content that was being held as a possible-prefix, so it is not dropped.
                if (planState.heldProse.length() > 0) {
                    String held = planState.heldProse.toString();
                    planState.heldProse.setLength(0);
                    return Flux.just(ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage(held))))
                            .build());
                }
                return Flux.empty();
            }
            if (round >= maxToolCalls) {
                return Flux.error(new IllegalStateException(
                        "Qwen model exceeded the maximum of " + maxToolCalls + " sequential tool calls."));
            }
            // Controlled loop detection. The request-scoped tracker catches both a
            // back-to-back identical signature and an alternating loop in which the
            // model re-invokes a tool that has already completed this turn - both
            // prohibited by the agent's system prompt. Either aborts safely rather
            // than exhausting the maxToolCalls budget.
            Optional<QwenToolCall> emittedCall = parser.parse(accumulated, registeredNames);
            String emittedName = emittedCall.map(QwenToolCall::name).orElse(null);
            if (loopGuard.register(accumulated.trim(), emittedName, planState.executed)
                    == ToolCallLoopTracker.Verdict.TERMINATE) {
                log.warn("Aborting tool loop after round {}: model repeated a completed or identical tool call; "
                        + "terminating with a controlled response.", round + 1);
                return Flux.just(controlledTerminationChatResponse());
            }
            log.debug("Detected complete qwen text tool call after {} chunks; executing via manager.", round + 1);
            ChatResponse synthetic = new ChatResponse(List.of(new Generation(new AssistantMessage(accumulated))));
            // Record the model-emitted tool so the read-tool plan can be marked satisfied.
            if (emittedCall.isPresent()) {
                planState.executed.add(emittedName);
            }
            return Mono.fromCallable(() -> toolCallingManager.executeToolCalls(requestPrompt, synthetic))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(result -> {
                        if (result.returnDirect()) {
                            return Flux.just(ChatResponse.builder().from(synthetic)
                                    .generations(ToolExecutionResult.buildGenerations(result))
                                    .build());
                        }
                        return streamWithToolExecution(
                                new Prompt(result.conversationHistory(), requestPrompt.getOptions()), round + 1,
                                loopGuard, planState);
                    });
        });

        return Flux.concat(prefix, Flux.concat(sanitized, continuation));
    }

    /**
     * Build the safe, controlled response returned when the model gets stuck
     * re-requesting one identical tool call. This is a plain natural-language
     * fallback that contains no tool-call JSON, does not fake a second tool call,
     * and never executes anything.
     */
    /**
     * The plain natural-language fallback returned when a tool loop is aborted.
     * Package-visible so tests can assert it contains no tool-call JSON.
     */
    static final String CONTROLLED_TERMINATION_MESSAGE =
            "I wasn't able to resolve that request — the tool lookup stalled before returning a result. "
                    + "Please rephrase or ask for the information again.";

    static ChatResponse controlledTerminationChatResponse() {
        AssistantMessage message = new AssistantMessage(CONTROLLED_TERMINATION_MESSAGE);
        return ChatResponse.builder().generations(List.of(new Generation(message))).build();
    }

    private static String textOf(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null) {
            return null;
        }
        return chunk.getResult().getOutput().getText();
    }

    private static Set<String> registeredToolNames(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions opts && opts.getToolCallbacks() != null) {
            return opts.getToolCallbacks().stream()
                    .map(cb -> cb.getToolDefinition().name())
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    /**
     * Merge runtime and default options exactly as the framework does when it
     * builds the per-request prompt (mirrors {@code OllamaChatModel#buildRequestPrompt},
     * which is package-private and not callable from this package). This is the only
     * piece of the base implementation that must be reproduced so the request prompt
     * carries the registered tool callbacks required by {@link QwenToolCallingManager}.
     */
    private Prompt mergeOptions(Prompt prompt) {
        OllamaChatOptions runtimeOptions = null;
        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof OllamaChatOptions ollamaChatOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(OllamaChatOptions.fromOptions(ollamaChatOptions),
                        OllamaChatOptions.class, OllamaChatOptions.class);
            }
            else if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
                        OllamaChatOptions.class);
            }
            else {
                runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
                        OllamaChatOptions.class);
            }
        }

        OllamaChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
                OllamaChatOptions.class);
        if (runtimeOptions != null) {
            requestOptions.setInternalToolExecutionEnabled(ModelOptionsUtils.mergeOption(
                    runtimeOptions.getInternalToolExecutionEnabled(),
                    this.defaultOptions.getInternalToolExecutionEnabled()));
            requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
                    this.defaultOptions.getToolNames()));
            requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(
                    runtimeOptions.getToolCallbacks(), this.defaultOptions.getToolCallbacks()));
            requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
                    this.defaultOptions.getToolContext()));
        }
        else {
            requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
            requestOptions.setToolNames(this.defaultOptions.getToolNames());
            requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
            requestOptions.setToolContext(this.defaultOptions.getToolContext());
        }
        return new Prompt(prompt.getInstructions(), requestOptions);
    }
}
