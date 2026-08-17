package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.config.AgentAiProperties;

/**
 * Application-side input guardrail for free-text user messages, evaluated
 * BEFORE the LLM is ever invoked. Fail-closed: any suspicious or
 * out-of-bounds input is rejected with a controlled verdict and the model is
 * not called at all.
 * <p>
 * Checks, in order:
 * <ol>
 *   <li>blank input</li>
 *   <li>message length over {@code agent.ai.max-message-length}</li>
 *   <li>forbidden control characters</li>
 *   <li>prompt-injection signal (see {@link PromptInjectionClassifier})</li>
 * </ol>
 */
public class InputGuardrails {

    private final AgentAiProperties properties;

    public InputGuardrails(AgentAiProperties properties) {
        this.properties = properties;
    }

    public record Verdict(boolean allowed, String errorCode, String message) {
        static Verdict ok() {
            return new Verdict(true, null, null);
        }
    }

    /**
     * Evaluate the message. Returns an {@code allowed=true} verdict for normal
     * input, or a fail-closed rejection otherwise.
     */
    public Verdict check(String message) {
        if (!properties.isEnableInputGuardrail()) {
            return Verdict.ok();
        }
        if (message == null || message.isBlank()) {
            return new Verdict(false, "INPUT_REQUIRED",
                    "Please provide a question or request.");
        }
        if (message.length() > properties.getMaxMessageLength()) {
            return new Verdict(false, "INPUT_TOO_LONG",
                    "Your message is too long. Please shorten it and try again.");
        }
        if (containsForbiddenControlChars(message)) {
            return new Verdict(false, "INVALID_INPUT",
                    "Your message contains unsupported characters. Please try again.");
        }
        if (PromptInjectionClassifier.classify(message) != PromptInjectionSignal.NONE) {
            return new Verdict(false, "INPUT_REJECTED",
                    "This request could not be processed safely. Please rephrase your question.");
        }
        return Verdict.ok();
    }

    private static boolean containsForbiddenControlChars(String message) {
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r') {
                return true;
            }
        }
        return false;
    }
}