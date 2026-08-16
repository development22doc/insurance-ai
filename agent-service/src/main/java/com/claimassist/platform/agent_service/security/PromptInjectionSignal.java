package com.claimassist.platform.agent_service.security;

/**
 * Classification of a prompt-injection signal detected in user input.
 * <p>
 * These are heuristic indicators only. The classifier is ONE defensive layer
 * in a defense-in-depth design - the authoritative security controls remain
 * application-enforced authentication, authorization, resource ownership and
 * input validation (which the LLM can never bypass regardless of its output).
 */
public enum PromptInjectionSignal {
    NONE,
    /** Attempt to override, ignore or replace the assistant's instructions. */
    INSTRUCTION_OVERRIDE,
    /** Attempt to extract the assistant's hidden system prompt/instructions. */
    SYSTEM_DISCLOSURE,
    /** Attempt to bypass authorization or access another user's data. */
    UNAUTHORIZED_ACCESS
}