package com.claimassist.platform.agent_service.controller;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.AgentQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    private final AgentGenerationService generation = mock(AgentGenerationService.class);
    private final AgentQueryService query = mock(AgentQueryService.class);
    private final AgentController controller = new AgentController(generation, query);

    @Test
    void streamChatMapsEachEventToSseWithData() {
        when(generation.streamResponse("hi", 99L)).thenReturn(Flux.just(
                StreamResponse.message("Hello ", "req-1"),
                StreamResponse.done("req-1")));

        List<ServerSentEvent<StreamResponse>> sse =
                controller.streamChat(new com.claimassist.platform.agent_service.dto.agent.AgentRequest("hi", 99L))
                        .collectList().block();

        assertThat(sse).hasSize(2);
        assertThat(sse.get(0).data().eventType()).isEqualTo("message");
        assertThat(sse.get(0).data().text()).isEqualTo("Hello ");
        assertThat(sse.get(0).data().requestId()).isEqualTo("req-1");
        assertThat(sse.get(1).data().done()).isTrue();
        assertThat(sse.get(1).data().eventType()).isEqualTo("done");
    }

    @Test
    void errorEventIsCarriedInSseData() {
        when(generation.streamResponse("hi", 1L)).thenReturn(Flux.just(
                StreamResponse.error("req-2", "OLLAMA_UNAVAILABLE", "AI is unavailable")));
        List<ServerSentEvent<StreamResponse>> sse =
                controller.streamChat(new com.claimassist.platform.agent_service.dto.agent.AgentRequest("hi", 1L))
                        .collectList().block();
        assertThat(sse.get(0).data().errorCode()).isEqualTo("OLLAMA_UNAVAILABLE");
        assertThat(sse.get(0).data().eventType()).isEqualTo("error");
    }

    @Test
    void getConversationHistoryDelegates() {
        when(query.getConversationHistory(5L)).thenReturn(List.of(mock(AgentMessageResponse.class)));
        assertThat(controller.getConversationHistory(5L).getBody()).hasSize(1);
    }
}