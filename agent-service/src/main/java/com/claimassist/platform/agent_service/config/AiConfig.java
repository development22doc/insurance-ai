package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.security.InputGuardrails;
import com.claimassist.platform.agent_service.security.OutputGuardrails;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
     * Provide an {@link OllamaApi} with an explicit connect/read timeout so a
     * hung or unreachable Ollama fails fast (Phase 7.1 / 7.2) instead of
     * hanging inside the agent budget. Supplying our own bean (the auto-config
     * is {@code @ConditionalOnMissingBean}) lets us inject the timeout while
     * preserving the configured base url. Streaming is still bounded by the
     * outer {@code agent.ai.agent-timeout-ms}; this timeout covers the
     * blocking chat path and the connect handshake.
     */
    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                               AgentAiProperties agentAiProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) agentAiProperties.getOllamaConnectTimeoutMs());
        factory.setReadTimeout((int) agentAiProperties.getOllamaReadTimeoutMs());
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    public InputGuardrails inputGuardrails(AgentAiProperties properties) {
        return new InputGuardrails(properties);
    }

    @Bean
    public OutputGuardrails outputGuardrails(AgentAiProperties properties) {
        return new OutputGuardrails(properties);
    }
}