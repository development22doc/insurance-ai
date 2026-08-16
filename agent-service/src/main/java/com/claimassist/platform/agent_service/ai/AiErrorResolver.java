package com.claimassist.platform.agent_service.ai;

import com.claimassist.platform.agent_service.ai.tool.ToolCallLimitExceededException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionTimeoutException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Maps the exceptions that can surface from the LLM/tool loop onto a
 * controlled, user-safe error descriptor. Never leaks stack traces, internal
 * class names, database details, secrets or infrastructure details to the
 * client - the message below is all a user ever sees.
 */
public final class AiErrorResolver {

    private AiErrorResolver() {}

    public record Resolved(String code, boolean retryable, String message) {}

    public static Resolved resolve(Throwable error) {
        // Walk the full cause chain so a wrapped tool/guard exception is still
        // classified by its specific type rather than falling through to MODEL_ERROR.
        Throwable cur = error;
        boolean sawConnectionRefused = false;
        while (cur != null) {
            if (cur instanceof ToolCallLimitExceededException) {
                return new Resolved("TOOL_CALL_LIMIT_REACHED", false,
                        "This request used up its tool budget and has been stopped to protect your data.");
            }
            if (cur instanceof ToolExecutionTimeoutException) {
                return new Resolved("TOOL_TIMEOUT", true,
                        "A backend lookup took too long and was stopped. Please try again.");
            }
            if (cur instanceof ToolExecutionException) {
                return new Resolved("TOOL_EXECUTION_FAILED", true,
                        "A backend service could not complete this request. Please try again.");
            }
            if (cur instanceof java.util.concurrent.TimeoutException) {
                return new Resolved("AGENT_TIMEOUT", true,
                        "The assistant took too long to respond. Please try again.");
            }
            if (cur instanceof ConnectException || cur instanceof WebClientRequestException
                    || isConnectionRefused(cur)) {
                sawConnectionRefused = true;
            }
            cur = cur.getCause();
        }

        if (sawConnectionRefused) {
            return new Resolved("OLLAMA_UNAVAILABLE", true,
                    "The AI service is currently unavailable. Please try again shortly.");
        }

        // Covers model-level errors, invalid responses and anything else.
        return new Resolved("MODEL_ERROR", true,
                "The assistant could not produce a response. Please try again.");
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