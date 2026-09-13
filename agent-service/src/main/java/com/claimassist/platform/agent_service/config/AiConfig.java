package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.security.InputGuardrails;
import com.claimassist.platform.agent_service.security.OutputGuardrails;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the real Spring AI {@link ChatClient} from the auto-configured
 * {@link ChatClient.Builder}. The underlying model is Ollama (configured via
 * {@code spring.ai.ollama.*}), but no agent business logic depends on Ollama
 * types - swapping in another provider later only changes configuration.
 */
@Configuration
@EnableConfigurationProperties(AgentAiProperties.class)
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * REMOVED custom OllamaApi bean to use Spring AI's default reactive streaming setup.
     * The custom bean with blocking RestClient was interfering with Spring AI's
     * reactive streaming pipeline. Spring AI's auto-configuration will create the OllamaApi
     * with proper reactive WebClient support using the configured base-url from properties.
     *
     * Timeout configuration is now handled via spring.ai.ollama initialization properties
     * in application-local-k8s.yaml, and the outer agent.ai.agent-timeout-ms for the
     * overall request budget.
     */

    @Bean
    public InputGuardrails inputGuardrails(AgentAiProperties properties) {
        return new InputGuardrails(properties);
    }

    @Bean
    public OutputGuardrails outputGuardrails(AgentAiProperties properties) {
        return new OutputGuardrails(properties);
    }
}
