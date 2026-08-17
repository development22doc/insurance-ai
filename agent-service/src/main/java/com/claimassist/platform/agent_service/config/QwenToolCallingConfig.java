package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.ai.model.QwenOllamaChatModel;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallingManager;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallParser;
import com.claimassist.platform.agent_service.ai.tool.QwenToolExecutionEligibilityPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaInitializationProperties;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Wires the qwen2.5-coder tool-call compatibility layer into the Spring AI
 * tool-calling pipeline.
 * <p>
 * Spring AI's {@code OllamaChatAutoConfiguration} provides the default
 * {@code OllamaChatModel} bean; since {@link QwenOllamaChatModel} IS an
 * {@code OllamaChatModel}, declaring it as a {@code @ConditionalOnMissingBean}
 * here makes the auto-configured model back off, so every chat request goes
 * through the streaming-compatible model. The framework's
 * {@code ToolCallingManager} and {@code ToolExecutionEligibilityPredicate}
 * auto-configurations already back off on our beans.
 */
@Configuration
public class QwenToolCallingConfig {

    @Bean
    public QwenToolCallParser qwenToolCallParser() {
        return new QwenToolCallParser();
    }

    @Bean
    public QwenToolCallingManager qwenToolCallingManager(QwenToolCallParser parser) {
        return new QwenToolCallingManager(parser);
    }

    @Bean
    public QwenToolExecutionEligibilityPredicate qwenToolExecutionEligibilityPredicate(QwenToolCallParser parser) {
        return new QwenToolExecutionEligibilityPredicate(parser);
    }

    /**
     * The primary model: a {@link QwenOllamaChatModel} that preserves native
     * Spring AI tool calls and additionally executes qwen2.5-coder's JSON-in-text
     * tool calls across streaming chunks.
     */
    @Bean
    @ConditionalOnMissingBean
    public QwenOllamaChatModel qwenOllamaChatModel(OllamaApi ollamaApi, OllamaChatProperties properties,
            OllamaInitializationProperties initProperties, ToolCallingManager toolCallingManager,
            QwenToolExecutionEligibilityPredicate predicate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            RetryTemplate retryTemplate, AgentAiProperties agentAiProperties) {
        PullModelStrategy chatPullStrategy = initProperties.getChat().isInclude()
                ? initProperties.getPullModelStrategy()
                : PullModelStrategy.NEVER;

        QwenOllamaChatModel model = new QwenOllamaChatModel(ollamaApi, properties.getOptions(), toolCallingManager,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                new ModelManagementOptions(chatPullStrategy, initProperties.getChat().getAdditionalModels(),
                        initProperties.getTimeout(), initProperties.getMaxRetries()),
                predicate, retryTemplate, agentAiProperties.getMaxToolCalls());

        observationConvention.ifAvailable(model::setObservationConvention);
        return model;
    }
}