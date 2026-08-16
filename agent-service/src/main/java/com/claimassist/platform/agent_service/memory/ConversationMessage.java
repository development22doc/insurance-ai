package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.common_lib.enums.MessageRole;

/**
 * A single entry of conversation history, in a lightweight, persistence-agnostic
 * form used only for building LLM context. It intentionally carries just the
 * role and content - never raw tool payloads, stack traces, secrets or internal
 * infrastructure details. Content is the safe, user-facing text already stored
 * on {@code agent_messages}.
 *
 * @param role    USER or ASSISTANT (SYSTEM/TOOL roles are excluded from context)
 * @param content the bounded, safe message text
 */
public record ConversationMessage(MessageRole role, String content) {
}