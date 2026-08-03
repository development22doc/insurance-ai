package com.claimassist.platform.agent_service.controller;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.dto.agent.AgentRequest;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.AgentQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {

    private final AgentGenerationService agentGenerationService;
    private final AgentQueryService agentQueryService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamResponse>> streamChat(@RequestBody @Valid AgentRequest request) {
        return agentGenerationService.streamResponse(request.message(), request.claimId())
                .map(data -> ServerSentEvent.<StreamResponse>builder().data(data).build());
    }

    @GetMapping("/claims/{claimId}")
    public ResponseEntity<List<AgentMessageResponse>> getConversationHistory(@PathVariable Long claimId) {
        return ResponseEntity.ok(agentQueryService.getConversationHistory(claimId));
    }
}
