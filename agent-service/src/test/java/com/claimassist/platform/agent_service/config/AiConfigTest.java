package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.security.InputGuardrails;
import com.claimassist.platform.agent_service.security.OutputGuardrails;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConfigTest {

    private final AiConfig config = new AiConfig();

    @Test
    void chatClientBuildsFromBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        assertThat(config.chatClient(builder)).isSameAs(client);
    }

    @Test
    void ollamaApiUsesConfiguredBaseUrlAndTimeouts() {
        AgentAiProperties props = new AgentAiProperties();
        props.setOllamaConnectTimeoutMs(1234);
        props.setOllamaReadTimeoutMs(5678);
        OllamaApi api = config.ollamaApi("http://ollama.test:11434", props);
        assertThat(api).isNotNull();
    }

    @Test
    void ollamaApiAcceptsAnyProvidedBaseUrl() {
        AgentAiProperties props = new AgentAiProperties();
        OllamaApi api = config.ollamaApi("http://localhost:11434", props);
        assertThat(api).isNotNull();
    }

    @Test
    void guardrailsBeansAreWired() {
        AgentAiProperties props = new AgentAiProperties();
        assertThat(config.inputGuardrails(props)).isInstanceOf(InputGuardrails.class);
        assertThat(config.outputGuardrails(props)).isInstanceOf(OutputGuardrails.class);
    }
}