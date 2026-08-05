package com.claimassist.platform.agent_service.ai;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatClientAdapter implements OptionalChatClient {

    private final Object realClient;

    public ChatClientAdapter(Object realClient) {
        this.realClient = realClient;
    }

    @Override
    public String invokeSimple(String userMessage, Object tools) {
        if (realClient == null) return "LLM not available";
        try {
            Class<?> clientClass = realClient.getClass();
            java.lang.reflect.Method prompt = clientClass.getMethod("prompt");
            Object promptBuilder = prompt.invoke(realClient);
            java.lang.reflect.Method system = promptBuilder.getClass().getMethod("system", String.class);
            Object withSystem = system.invoke(promptBuilder, "[system]");
            java.lang.reflect.Method user = withSystem.getClass().getMethod("user", String.class);
            Object withUser = user.invoke(withSystem, userMessage);
            java.lang.reflect.Method toolsMethod = withUser.getClass().getMethod("tools", Object.class);
            Object withTools = toolsMethod.invoke(withUser, tools);
            java.lang.reflect.Method stream = withTools.getClass().getMethod("stream");
            Object streamObj = stream.invoke(withTools);
            java.lang.reflect.Method chatResponse = streamObj.getClass().getMethod("chatResponse");
            Object responses = chatResponse.invoke(streamObj);
            // For simplicity, attempt toString on responses
            return responses == null ? "" : responses.toString();
        } catch (Exception e) {
            log.warn("ChatClientAdapter reflective invocation failed: {}", e.toString());
            return "LLM invocation failed locally";
        }
    }
}

