package com.claimassist.platform.agent_service.service;

import com.claimassist.platform.agent_service.dto.agent.StreamResponse;
import reactor.core.publisher.Flux;

public interface AgentGenerationService {
    Flux<StreamResponse> streamResponse(String userMessage, Long claimId);
}
