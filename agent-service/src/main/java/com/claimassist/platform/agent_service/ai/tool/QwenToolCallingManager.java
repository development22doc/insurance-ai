package com.claimassist.platform.agent_service.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ToolCallingManager} that preserves Spring AI's native tool-calling
 * behaviour exactly and adds compatibility for models (such as qwen2.5-coder)
 * that emit a structured tool call as JSON text inside assistant content rather
 * than as a native {@code message.tool_calls}.
 * <p>
 * Execution always resolves against the existing {@link ToolCallback} objects
 * that Spring AI already received for the request (the ones wrapped by
 * {@link ToolExecutionGuard}), so every Qwen text tool call flows through the
 * same {@link GuardedToolCallback} -&gt; {@link ToolExecutionGuard} -&gt;
 * {@code InsuranceAgentTools} path as a native call. No second execution
 * framework is introduced; the native branch is delegated unchanged to a wrapped
 * {@link DefaultToolCallingManager}.
 */
public class QwenToolCallingManager implements ToolCallingManager {

    private static final ObjectMapper SERIALIZER = new ObjectMapper();

    private final ToolCallingManager delegate;
    private final QwenToolCallParser parser;

    public QwenToolCallingManager(QwenToolCallParser parser) {
        this(parser, ToolCallingManager.builder().build());
    }

    public QwenToolCallingManager(QwenToolCallParser parser, ToolCallingManager delegate) {
        this.parser = parser;
        this.delegate = delegate;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        Optional<Generation> toolCallGeneration = chatResponse.getResults()
                .stream()
                .filter(g -> !g.getOutput().getToolCalls().isEmpty())
                .findFirst();

        // Native Spring AI tool calls: behave exactly as the framework default.
        if (toolCallGeneration.isPresent()) {
            return delegate.executeToolCalls(prompt, chatResponse);
        }

        // Qwen text tool-call compatibility branch.
        String assistantText = assistantText(chatResponse);
        List<ToolCallback> callbacks = toolCallbacks(prompt);
        Set<String> registeredNames = callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        Optional<QwenToolCall> parsed = parser.parse(assistantText, registeredNames);
        if (parsed.isEmpty()) {
            throw new IllegalStateException(
                    "Model output does not contain a valid registered tool call; refusing to execute.");
        }
        QwenToolCall toolCall = parsed.get();

        ToolCallback callback = callbacks.stream()
                .filter(cb -> cb.getToolDefinition().name().equals(toolCall.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Tool '" + toolCall.name() + "' is not a registered/guarded callback; refusing to execute."));

        String argumentsJson = toArgumentsJson(toolCall.arguments());
        String toolId = "qwen-" + UUID.randomUUID();
        ToolContext toolContext = new ToolContext(Map.of());

        String result = callback.call(argumentsJson, toolContext);

        AssistantMessage assistantMessage = new AssistantMessage(assistantText);
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(toolId, toolCall.name(), result)))
                .build();

        List<Message> history = new java.util.ArrayList<>(prompt.getInstructions());
        history.add(assistantMessage);
        history.add(toolResponseMessage);

        return ToolExecutionResult.builder()
                .conversationHistory(history)
                .returnDirect(false)
                .build();
    }

    /**
     * Execute a planner-supplied {@link QwenToolCall} through the same guarded
     * {@link ToolCallback} path as a model-emitted call. Used by the streaming
     * model to complete the READ-only plan for a multi-part request when an
     * unreliable model stops short of emitting every needed tool.
     * <p>
     * Resolution is limited to the request's registered, guarded callbacks only
     * - the same set Spring AI already received (wrapped by
     * {@link ToolExecutionGuard}) - so a planned call can never bypass the guard.
     * The result is returned as ordinary conversation history so the model can
     * ground its final answer in the freshly fetched data.
     */
    public ToolExecutionResult executePlannedToolCall(Prompt prompt, QwenToolCall toolCall) {
        List<ToolCallback> callbacks = toolCallbacks(prompt);
        String name = toolCall.name();
        ToolCallback callback = callbacks.stream()
                .filter(cb -> cb.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Planned tool '" + name + "' is not a registered/guarded callback; refusing to execute."));

        String argumentsJson = toArgumentsJson(toolCall.arguments());
        String toolId = "qwen-planned-" + UUID.randomUUID();
        ToolContext toolContext = new ToolContext(Map.of());

        String result = callback.call(argumentsJson, toolContext);

        AssistantMessage assistantMessage = new AssistantMessage(renderToolCallJson(toolCall));
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(toolId, name, result)))
                .build();

        List<Message> history = new java.util.ArrayList<>(prompt.getInstructions());
        history.add(assistantMessage);
        history.add(toolResponseMessage);

        return ToolExecutionResult.builder()
                .conversationHistory(history)
                .returnDirect(false)
                .build();
    }

    private static String renderToolCallJson(QwenToolCall toolCall) {
        try {
            Map<String, Object> call = new java.util.LinkedHashMap<>();
            call.put("name", toolCall.name());
            call.put("arguments", toolCall.arguments());
            return SERIALIZER.writeValueAsString(call);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to render planned tool call", e);
        }
    }

    private static String assistantText(ChatResponse chatResponse) {
        if (chatResponse.getResults() == null) {
            return null;
        }
        return chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + b);
    }

    private static List<ToolCallback> toolCallbacks(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions opts) {
            return opts.getToolCallbacks();
        }
        return List.of();
    }

    private static String toArgumentsJson(Map<String, Object> arguments) {
        try {
            return SERIALIZER.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize validated tool arguments", e);
        }
    }
}