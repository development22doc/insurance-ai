package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimSagaStepProcessorListener {

    private final ObjectMapper objectMapper;
    private final ClaimSagaStepProcessorService processorService;

    @Transactional
    @KafkaListener(
            topics = ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC,
            groupId = "claims-saga-step-processor-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onStepCommand(String rawMessage) throws Exception {
        ClaimSagaStepCommandEvent command =
                objectMapper.readValue(rawMessage, ClaimSagaStepCommandEvent.class);
        processorService.processStep(command);
    }
}

