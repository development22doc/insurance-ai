package com.claimassist.platform.agent_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * SimpleLoggerAdvisor logs every prompt/response - in a regulated domain
     * like insurance this is not just a debugging convenience, it's part of
     * the audit trail: every tool call and every response the agent gave is
     * traceable, independent of what ended up in the AgentEvent table.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }
}
