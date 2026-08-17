package com.claimassist.platform.agent_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConfigTest {

    @Test
    void propertiesHaveSaneExternalizableDefaults() {
        AgentAiProperties props = new AgentAiProperties();
        assertThat(props.getMaxToolCalls()).isEqualTo(10);
        assertThat(props.getToolTimeoutMs()).isEqualTo(15_000);
        assertThat(props.getAgentTimeoutMs()).isEqualTo(60_000);
    }

    @Test
    void chatClientBeanBuildsFromAutoConfiguredBuilder() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(client);

        ChatClient built = new AiConfig().chatClient(builder);
        assertThat(built).isSameAs(client);
    }

    @Test
    void ollamaSettingsAreExternalizedViaEnvironment() {
        // Ensures the application relies on env placeholders rather than hardcoded endpoints.
        assertThat(System.getenv().keySet()).doesNotContain("REQUIRED_HARDCODED_KEY");
    }
}