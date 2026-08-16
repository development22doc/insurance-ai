package com.claimassist.platform.agent_service.ai;

import com.claimassist.platform.agent_service.ai.AiErrorResolver.Resolved;
import com.claimassist.platform.agent_service.ai.tool.ToolCallLimitExceededException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiErrorResolverTest {

    @Test
    void mapsToolCallLimit() {
        Resolved r = AiErrorResolver.resolve(new ToolCallLimitExceededException(5));
        assertThat(r.code()).isEqualTo("TOOL_CALL_LIMIT_REACHED");
        assertThat(r.retryable()).isFalse();
    }

    @Test
    void mapsToolTimeout() {
        Resolved r = AiErrorResolver.resolve(new ToolExecutionTimeoutException("get_claim_status", 15000));
        assertThat(r.code()).isEqualTo("TOOL_TIMEOUT");
        assertThat(r.retryable()).isTrue();
    }

    @Test
    void mapsToolExecutionFailure() {
        Resolved r = AiErrorResolver.resolve(new ToolExecutionException("get_claim_documents",
                new IllegalStateException("boom")));
        assertThat(r.code()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(r.retryable()).isTrue();
    }

    @Test
    void mapsGenericTimeoutToAgentTimeout() {
        Resolved r = AiErrorResolver.resolve(new TimeoutException("idle"));
        assertThat(r.code()).isEqualTo("AGENT_TIMEOUT");
    }

    @Test
    void mapsConnectionRefusedToOllamaUnavailable() {
        Resolved r = AiErrorResolver.resolve(new ConnectException("Connection refused: 11434"));
        assertThat(r.code()).isEqualTo("OLLAMA_UNAVAILABLE");
        assertThat(r.retryable()).isTrue();
    }

    @Test
    void mapsWebClientRequestToOllamaUnavailable() throws Exception {
        Resolved r = AiErrorResolver.resolve(
                new WebClientRequestException(new ConnectException("failed"),
                        org.springframework.http.HttpMethod.GET,
                        new java.net.URI("http://localhost:11434"),
                        new org.springframework.http.HttpHeaders()));
        assertThat(r.code()).isEqualTo("OLLAMA_UNAVAILABLE");
    }

    @Test
    void mapsUnknownErrorsToModelError() {
        Resolved r = AiErrorResolver.resolve(new IllegalStateException("unexpected"));
        assertThat(r.code()).isEqualTo("MODEL_ERROR");
        assertThat(r.message()).isNotBlank();
    }

    @Test
    void neverLeaksClassOrMessageDetail() {
        Resolved r = AiErrorResolver.resolve(
                new RuntimeException(new IllegalStateException("jdbc:postgresql://secret-db:5432")));
        assertThat(r.message()).doesNotContain("jdbc").doesNotContain("secret-db")
                .doesNotContain("IllegalStateException").doesNotContain("postgresql");
    }
}