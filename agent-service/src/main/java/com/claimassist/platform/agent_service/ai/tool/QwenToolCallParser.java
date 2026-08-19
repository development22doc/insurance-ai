package com.claimassist.platform.agent_service.ai.tool;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic parser and input-boundary validator for the structured tool-call
 * object that qwen2.5-coder emits inside assistant text.
 * <p>
 * The parser accepts ONLY a complete, strict JSON object of the shape
 * {@code { "name": "<registered_tool>", "arguments": { ... } }} and rejects or
 * ignores everything else. It performs no tool execution, uses Jackson only
 * (never regex), and never reflects over model output. Security is not delegated
 * to prompt instructions: the parser validates structure, the
 * {@link QwenToolCallingManager} validates that the name resolves to a
 * registered, guarded callback, and the existing {@link ToolExecutionGuard} and
 * the tool method itself remain authoritative for authorization and argument
 * rules.
 *
 * @implNote {@code classify} is streaming-friendly: an incomplete JSON value
 * (e.g. a partial object spread across successive content chunks) is reported as
 * {@link Classification#POSSIBLE_PREFIX} so a caller can keep accumulating
 * before deciding.
 */
public final class QwenToolCallParser {

    /** Tri-state for a given content buffer, used for streaming accumulation. */
    public enum Classification {
        /** Content is a complete, valid, registered tool-call object. */
        TOOL_CALL,
        /** Content is an incomplete prefix that may still become a tool call. */
        POSSIBLE_PREFIX,
        /** Content is definitively NOT a valid tool call (prose / invalid JSON). */
        NOT_A_TOOL_CALL
    }

    private static final long DEFAULT_MAX_ARGS_CHARS = 4000L;

    /** Factory configured to reject duplicate JSON properties within one object. */
    private final ObjectMapper mapper = new ObjectMapper(
            new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    private final long maxArgumentsChars;

    public QwenToolCallParser() {
        this(DEFAULT_MAX_ARGS_CHARS);
    }

    public QwenToolCallParser(long maxArgumentsChars) {
        this.maxArgumentsChars = Math.max(1, maxArgumentsChars);
    }

    /**
     * Strictly parse {@code content} as a valid, registered tool call.
     *
     * @return the parsed call, or empty if the content is not a complete, valid,
     * registered structured tool-call object.
     */
    public Optional<QwenToolCall> parse(String content, Set<String> registeredNames) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String trimmed = content.trim();
        if (trimmed.length() > maxArgumentsChars) {
            return Optional.empty();
        }
        JsonNode node = tryParseJson(trimmed);
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        QwenToolCall call = validate(node, registeredNames);
        return call == null ? Optional.empty() : Optional.of(call);
    }

    /**
     * Classify a content buffer for streaming accumulation. Never throws.
     */
    public Classification classify(String content, Set<String> registeredNames) {
        if (content == null || content.isBlank()) {
            return Classification.POSSIBLE_PREFIX;
        }
        String trimmed = content.trim();
        if (trimmed.length() > maxArgumentsChars) {
            return Classification.NOT_A_TOOL_CALL;
        }
        JsonNode node = tryParseComplete(trimmed);
        if (node != null) {
            if (node.isObject() && validate(node, registeredNames) != null) {
                return Classification.TOOL_CALL;
            }
            // A complete JSON value that is not a valid tool call is prose / arbitrary JSON.
            return Classification.NOT_A_TOOL_CALL;
        }
        return parseErrorKind(trimmed) == ParseErrorKind.INCOMPLETE
                ? Classification.POSSIBLE_PREFIX
                : Classification.NOT_A_TOOL_CALL;
    }

    private enum ParseErrorKind { INCOMPLETE, MALFORMED }

    /** Read a complete JSON value, or null if the input is malformed/incomplete. */
    private JsonNode tryParseComplete(String trimmed) {
        try {
            return mapper.readTree(trimmed);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Distinguish an incomplete JSON prefix (input exhausted) from a malformed value. */
    private ParseErrorKind parseErrorKind(String trimmed) {
        try {
            mapper.readTree(trimmed);
            return ParseErrorKind.MALFORMED;
        } catch (JacksonException e) {
            // Jackson reports premature end-of-input both as JsonEOFException and, for a
            // partially-read object (e.g. a trailing key-separator comma before EOF), as a
            // MismatchedInputException. Both carry an "end-of-input" detail; anything else
            // (duplicate key, unexpected character/token, ...) is genuinely malformed.
            String msg = e.getOriginalMessage();
            return (msg != null && msg.contains("end-of-input"))
                    ? ParseErrorKind.INCOMPLETE
                    : ParseErrorKind.MALFORMED;
        }
    }

    /** True when {@code content} is a complete, valid, registered tool call. */
    public boolean isCompleteToolCall(String content, Set<String> registeredNames) {
        return classify(content, registeredNames) == Classification.TOOL_CALL;
    }

    /** Read a complete JSON document; malformed or incomplete input yields null. */
    private JsonNode tryParseJson(String trimmed) {
        try {
            return mapper.readTree(trimmed);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static QwenToolCall validate(JsonNode node, Set<String> registeredNames) {
        // Strict field set: exactly "name" and "arguments".
        Iterator<String> fields = node.fieldNames();
        boolean hasName = false;
        boolean hasArguments = false;
        while (fields.hasNext()) {
            String field = fields.next();
            switch (field) {
                case "name" -> hasName = true;
                case "arguments" -> hasArguments = true;
                default -> {
                    return null; // unexpected top-level field -> reject
                }
            }
        }
        if (!hasName || !hasArguments) {
            return null; // missing required field
        }

        JsonNode nameNode = node.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return null;
        }
        String name = nameNode.asText().trim();
        if (!isSafeToolName(name)) {
            return null;
        }
        if (registeredNames != null && !registeredNames.isEmpty() && !registeredNames.contains(name)) {
            return null; // not a registered tool
        }

        JsonNode argsNode = node.get("arguments");
        if (argsNode == null || !argsNode.isObject()) {
            return null; // arguments must be an object
        }
        if (argsNode.size() == 0) {
            // An empty object is valid (no-arg tools); still allow it.
        }

        Map<String, Object> args = new LinkedHashMap<>();
        Iterator<String> argFields = argsNode.fieldNames();
        while (argFields.hasNext()) {
            String key = argFields.next();
            args.put(key, jsonToJava(argsNode.get(key)));
        }
        return new QwenToolCall(name, args);
    }

    /** Convert a JsonNode to a plain Java value (safe, never reflected from attacker input). */
    private static Object jsonToJava(JsonNode node) {
        return switch (node.getNodeType()) {
            case OBJECT -> {
                java.util.Map<String, Object> m = new LinkedHashMap<>();
                Iterator<String> it = node.fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, jsonToJava(node.get(k)));
                }
                yield m;
            }
            case ARRAY -> {
                java.util.List<Object> l = new java.util.ArrayList<>();
                node.forEach(child -> l.add(jsonToJava(child)));
                yield l;
            }
            case STRING -> node.asText();
            case NUMBER -> node.isIntegralNumber() ? node.asLong() : node.asDouble();
            case BOOLEAN -> node.asBoolean();
            case NULL -> null;
            default -> node.asText();
        };
    }

    /**
     * A safe tool name is a short, non-blank identifier without characters that
     * could denote a class or method ('.' '/', whitespace, etc.), so an LLM can
     * never reference an arbitrary Java class/method.
     */
    private static boolean isSafeToolName(String name) {
        if (name.isEmpty() || name.length() > 128) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}