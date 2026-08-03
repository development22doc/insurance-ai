package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.common_lib.event.ClaimSagaOrchestrationRequestEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
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
    public void onOrchestrationRequest(
            @Payload String rawMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "kafka_receivedPartitionId", required = false) Integer partition,
            @Header(name = "kafka_offset", required = false) Long offset,
            Acknowledgment ack) throws Exception {
        try {
            ClaimSagaOrchestrationRequestEvent request =
                    objectMapper.readValue(rawMessage, ClaimSagaOrchestrationRequestEvent.class);
            orchestratorService.startOrchestration(request);
            ack.acknowledge();
            log.debug("Orchestration request acknowledged - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
        } catch (Exception e) {
            log.error("Error processing orchestration request from topic: {}, partition: {}, offset: {}",
                    topic, partition, offset, e);
            throw e;
        }
    }

    @Transactional
    @KafkaListener(
            topics = ClaimSagaOrchestratorService.STEP_RESULT_TOPIC,
            groupId = "claims-saga-orchestrator-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onStepResult(
            @Payload String rawMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "kafka_receivedPartitionId", required = false) Integer partition,
            @Header(name = "kafka_offset", required = false) Long offset,
            Acknowledgment ack) throws Exception {
        try {
            ClaimSagaStepResultEvent result =
                    objectMapper.readValue(rawMessage, ClaimSagaStepResultEvent.class);
            orchestratorService.handleStepResult(result);
            ack.acknowledge();
            log.debug("Step result acknowledged - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
        } catch (Exception e) {
            log.error("Error processing step result from topic: {}, partition: {}, offset: {}",
                    topic, partition, offset, e);
            throw e;
        }
    }
}

