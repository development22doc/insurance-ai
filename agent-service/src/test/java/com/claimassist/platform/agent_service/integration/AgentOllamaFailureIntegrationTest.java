package com.claimassist.platform.agent_service.integration;

import com.claimassist.platform.agent_service.ai.AiErrorResolver;
import com.claimassist.platform.agent_service.support.OllamaTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import reactor.core.Disposable;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure-mode tests for the real Ollama integration: unavailable runtime,
 * invalid model, and client cancellation during streaming.
 */
class AgentOllamaFailureIntegrationTest {

    @Test
    void ollamaUnavailableYieldsConnectionErrorNotStackTrace() throws Exception {
        OllamaApi api = OllamaApi.builder().baseUrl("http://localhost:1").build(); // nothing listens here
        ChatModel model = OllamaChatModel.builder().ollamaApi(api)
                .defaultOptions(OllamaChatOptions.builder().model("x").numCtx(2048).build()).build();
        ChatClient client = ChatClient.builder(model).build();

        Throwable error = captureError(client);
        AiErrorResolver.Resolved resolved = AiErrorResolver.resolve(error);
        assertThat(resolved.code()).isEqualTo("OLLAMA_UNAVAILABLE");
        assertThat(resolved.message()).doesNotContain("localhost");
    }

    @Test
    @Tag("ollama")
    void invalidModelFailsCleanly() throws Exception {
        OllamaTestSupport.assumeOllamaAvailable();
        ChatModel model = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(OllamaTestSupport.BASE_URL).build())
                .defaultOptions(OllamaChatOptions.builder()
                        .model("model-that-does-not-exist-xyz").numCtx(2048).build()).build();
        ChatClient client = ChatClient.builder(model).build();

        Throwable error = captureError(client);
        assertThat(error).isNotNull();
        AiErrorResolver.Resolved resolved = AiErrorResolver.resolve(error);
        assertThat(resolved.message()).isNotBlank();
    }

    @Test
    @Tag("ollama")
    void clientDisconnectDoesNotLeak() throws Exception {
        OllamaTestSupport.assumeOllamaAvailable();
        ChatClient client = OllamaTestSupport.chatClient();

        AtomicReference<SignalType> signal = new AtomicReference<>();
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<Disposable> holder = new AtomicReference<>();

        holder.set(client.prompt().user("Say hello")
                .stream().content()
                .doFinally(s -> {
                    signal.set(s);
                    terminal.countDown();
                })
                .subscribe());

        // Client disconnects (cancels) mid-stream.
        holder.get().dispose();

        // The stream must terminate cleanly (no hang, no resource leak).
        assertThat(terminal.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(holder.get().isDisposed()).isTrue();
        assertThat(signal.get()).isEqualTo(SignalType.CANCEL);
    }

    /** Subscribes without rethrowing; returns the terminal error (or null on completion). */
    private Throwable captureError(ChatClient client) throws InterruptedException {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        client.prompt().user("hi").stream().content()
                .subscribe(null,
                        e -> { error.set(e); done.countDown(); },
                        done::countDown);
        done.await(30, TimeUnit.SECONDS);
        return error.get();
    }
}