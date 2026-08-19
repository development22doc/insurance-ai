package com.claimassist.platform.agent_service.observability;

import com.claimassist.platform.agent_service.ai.AiErrorResolver;
import com.claimassist.platform.agent_service.ai.tool.ToolCallLimitExceededException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionTimeoutException;

import java.util.concurrent.TimeoutException;

/**
 * Consistent, bounded set of operational error categories (Phase 6.8).
 * <p>
 * Kept deliberately small and non-overlapping so engineers can answer "did the
 * request fail because of the LLM, the tool, the backend, the cache, the
 * authorization layer, or another component?" without a sprawling taxonomy.
 * <p>
 * {@link #classify(Throwable)} maps the exceptions that already surface from
 * the LLM/tool loop onto these categories. It reuses the same exception types
 * that {@link AiErrorResolver} already understands, so classification is
 * consistent with the user-facing error the client receives.
 */
public enum AgentErrorCategory {

    LLM_ERROR,
    TOOL_ERROR,
    AUTHORIZATION_ERROR,
    VALIDATION_ERROR,
    BACKEND_UNAVAILABLE,
    TIMEOUT,
    CACHE_ERROR,
    PROMPT_GUARDRAIL_REJECTION,
    CONTEXT_ERROR,
    PERSISTENCE_ERROR,
    UNKNOWN_ERROR;

    /**
     * Classify a thrown error onto an operational category by walking its cause
     * chain, mirroring how {@link AiErrorResolver} classifies for the client so
     * observability and the user-facing error agree.
     */
    public static AgentErrorCategory classify(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof ToolCallLimitExceededException
                    || cur instanceof ToolExecutionException
                    || cur instanceof ToolExecutionTimeoutException) {
                return TOOL_ERROR;
            }
            if (cur instanceof TimeoutException) {
                return TIMEOUT;
            }
            if (isConnectionRefused(cur)) {
                return BACKEND_UNAVAILABLE;
            }
            cur = cur.getCause();
        }
        return UNKNOWN_ERROR;
    }

    /**
     * Map a known {@link AiErrorResolver} code onto an operational category so
     * the reactive error path (which already resolves to a code) can be
     * categorised without re-walking the exception.
     */
    public static AgentErrorCategory fromCode(String code) {
        if (code == null) {
            return UNKNOWN_ERROR;
        }
        return switch (code) {
            case "OLLAMA_UNAVAILABLE", "MODEL_ERROR" -> LLM_ERROR;
            case "TOOL_CALL_LIMIT_REACHED", "TOOL_EXECUTION_FAILED", "TOOL_TIMEOUT" -> TOOL_ERROR;
            case "AGENT_TIMEOUT" -> TIMEOUT;
            case "INPUT_REJECTED", "PROMPT_GUARDRAIL_REJECTED" -> PROMPT_GUARDRAIL_REJECTION;
            case "UNAUTHORIZED" -> AUTHORIZATION_ERROR;
            case "INVALID_TOOL_ARGUMENTS" -> VALIDATION_ERROR;
            default -> UNKNOWN_ERROR;
        };
    }

    private static boolean isConnectionRefused(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("Connection refused") || msg.contains("ConnectException")
                    || msg.contains("Failed to connect"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}