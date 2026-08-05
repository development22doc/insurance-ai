package com.claimassist.platform.agent_service.ai;

/**
 * Marker interface for an optional chat client adapter. Implementations
 * delegate to the real spring-ai ChatClient reflectively when available.
 */
public interface OptionalChatClient {

    /**
     * Invoke a simple prompt flow and return a textual result for local
     * fallback usage. Implementations may use reflection against the
     * underlying ChatClient.
     */
    String invokeSimple(String userMessage, Object tools);
}

