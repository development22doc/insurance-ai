package com.claimassist.platform.agent_service.dto.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the SSE payload factories added in Phase 1 plus the backward-compatible
 * {@link StreamResponse#StreamResponse(String)} constructor.
 */
class StreamResponseTest {

    @Test
    void legacyConstructorIsAMessageEvent() {
        StreamResponse r = new StreamResponse("hello");
        assertThat(r.text()).isEqualTo("hello");
        assertThat(r.eventType()).isEqualTo("message");
        assertThat(r.done()).isFalse();
        assertThat(r.requestId()).isNull();
        assertThat(r.errorCode()).isNull();
    }

    @Test
    void messageFactoryCarriesTokenChunkAndRequestId() {
        StreamResponse r = StreamResponse.message("tok", "req-1");
        assertThat(r.text()).isEqualTo("tok");
        assertThat(r.eventType()).isEqualTo("message");
        assertThat(r.requestId()).isEqualTo("req-1");
        assertThat(r.done()).isFalse();
    }

    @Test
    void doneFactoryMarksTerminalSuccessfulEvent() {
        StreamResponse r = StreamResponse.done("req-1");
        assertThat(r.eventType()).isEqualTo("done");
        assertThat(r.done()).isTrue();
        assertThat(r.text()).isEmpty();
        assertThat(r.requestId()).isEqualTo("req-1");
    }

    @Test
    void errorFactoryCarriesSafeCodeAndMessage() {
        StreamResponse r = StreamResponse.error("req-1", "OLLAMA_UNAVAILABLE", "AI service unavailable");
        assertThat(r.eventType()).isEqualTo("error");
        assertThat(r.done()).isTrue();
        assertThat(r.errorCode()).isEqualTo("OLLAMA_UNAVAILABLE");
        assertThat(r.text()).isEqualTo("AI service unavailable");
        assertThat(r.requestId()).isEqualTo("req-1");
    }
}