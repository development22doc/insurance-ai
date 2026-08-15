package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.claimassist.platform.common_lib.messaging.AckUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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
    public void onStepCommand(String rawMessage, Acknowledgment ack) throws Exception {
        ClaimSagaStepCommandEvent command =
                objectMapper.readValue(rawMessage, ClaimSagaStepCommandEvent.class);
        processorService.processStep(command);
        // Acknowledge only after the transaction commits (dedup + business state change
        // must commit before the offset advances). On rollback no ack -> redelivery.
        AckUtils.acknowledgeAfterCommit(ack);
    }
}

