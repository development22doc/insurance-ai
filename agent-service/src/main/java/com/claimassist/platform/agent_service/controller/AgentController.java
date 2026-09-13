package com.claimassist.platform.agent_service.controller;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.dto.agent.AgentRequest;
import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import com.claimassist.platform.agent_service.service.AgentGenerationService;
import com.claimassist.platform.agent_service.service.AgentQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
@Slf4j
public class AgentController {

    private final AgentGenerationService agentGenerationService;
    private final AgentQueryService agentQueryService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamResponse>> streamChat(@RequestBody @Valid AgentRequest request) {
        // Diagnostic logging at controller entry point
        String requestId = java.util.UUID.randomUUID().toString();
        log.info("AGENT CONTROLLER DIAGNOSTICS [requestId={}]: endpoint=/agent/stream, claimId={}, message_length={}",
                requestId, request.claimId(), request.message() != null ? request.message().length() : 0);

        return ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (auth != null) {
                        log.info("AGENT CONTROLLER DIAGNOSTICS [requestId={}]: security_context_present=true, authentication_class={}, authenticated={}",
                                requestId, auth.getClass().getSimpleName(), auth.isAuthenticated());
                    } else {
                        log.warn("AGENT CONTROLLER DIAGNOSTICS [requestId={}]: security_context_present=true, authentication=null",
                                requestId);
                    }
                })
                .switchIfEmpty(reactor.core.publisher.Mono.defer(() -> {
                    log.warn("AGENT CONTROLLER DIAGNOSTICS [requestId={}]: security_context_present=false", requestId);
                    return reactor.core.publisher.Mono.empty();
                }))
                .flatMapMany(ctx -> agentGenerationService.streamResponse(request.message(), request.claimId())
                        .map(data -> ServerSentEvent.<StreamResponse>builder().data(data).build()));
    }

    @GetMapping("/claims/{claimId}")
    public ResponseEntity<List<AgentMessageResponse>> getConversationHistory(@PathVariable Long claimId) {
        return ResponseEntity.ok(agentQueryService.getConversationHistory(claimId));
    }
}
