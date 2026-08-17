package com.claimassist.platform.agent_service.support;

import com.claimassist.platform.agent_service.ai.model.QwenOllamaChatModel;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallParser;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallingManager;
import com.claimassist.platform.agent_service.ai.tool.QwenToolExecutionEligibilityPredicate;
import org.junit.jupiter.api.Assumptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.retry.RetryUtils;

import java.time.Duration;

/**
 * Shared helpers for tests that require a REAL Ollama instance. These tests
 * are skipped automatically (not failed) when Ollama is not reachable, so the
 * build stays green on machines without a local model.
 */
public final class OllamaTestSupport {

    public static final String BASE_URL =
            System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    public static final String MODEL =
            System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");

    private OllamaTestSupport() {}

    /** Skip the test unless a model is actually running locally. */
    public static void assumeOllamaAvailable() {
        Assumptions.assumeTrue(probe(), "Ollama not reachable at " + BASE_URL + " - skipping real-LLM test.");
    }

    private static boolean probe() {
        try {
            var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(BASE_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Build a ChatClient wired to a real Ollama instance, using the
     * {@link QwenOllamaChatModel} so Qwen JSON-in-text tool calls (emitted by
     * qwen2.5-coder across streaming chunks) are executed through the same
     * guarded pipeline as native calls.
     */
    public static ChatClient chatClient() {
        OllamaApi api = OllamaApi.builder().baseUrl(BASE_URL).build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(MODEL)
                .temperature(0.2)
                .numCtx(8192)
                .build();
        QwenToolCallParser parser = new QwenToolCallParser();
        ChatModel model = new QwenOllamaChatModel(api, options,
                new QwenToolCallingManager(parser),
                io.micrometer.observation.ObservationRegistry.NOOP,
                ModelManagementOptions.defaults(),
                new QwenToolExecutionEligibilityPredicate(parser),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        return ChatClient.builder(model).build();
    }
}