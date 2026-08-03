package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.common_lib.event.ClaimSagaOrchestrationRequestEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimSagaOrchestrationListener {

    private final ObjectMapper objectMapper;
    private final ClaimSagaOrchestratorService orchestratorService;

    @Transactional
    @KafkaListener(
            topics = ClaimSagaOrchestratorService.ORCHESTRATION_REQUEST_TOPIC,
            groupId = "claims-saga-orchestrator-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onOrchestrationRequest(String rawMessage) throws Exception {
        ClaimSagaOrchestrationRequestEvent request =
                objectMapper.readValue(rawMessage, ClaimSagaOrchestrationRequestEvent.class);
        orchestratorService.startOrchestration(request);
    }

    @Transactional
    @KafkaListener(
            topics = ClaimSagaOrchestratorService.STEP_RESULT_TOPIC,
            groupId = "claims-saga-orchestrator-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onStepResult(String rawMessage) throws Exception {
        ClaimSagaStepResultEvent result =
                objectMapper.readValue(rawMessage, ClaimSagaStepResultEvent.class);
        orchestratorService.handleStepResult(result);
    }
}

