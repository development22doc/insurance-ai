package com.claimassist.platform.claims_service.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Claims-specific Kafka topic configuration for the outbox pattern.
 * <p>
 * Producer, consumer, and error handler configuration are provided by
 * SharedKafkaAutoConfiguration from common-lib. This class defines only
 * the claims-specific topic beans.
 * <p>
 * Claims service requires additional saga orchestration topics compared to
 * agent service, reflecting its more complex business logic for multi-step
 * transaction coordination.
 */
@Configuration
public class OutboxKafkaConfig {

    private static final String CLAIM_UPDATE_REQUEST_TOPIC = "claim-update-request-event";
    private static final String CLAIM_UPDATE_RESPONSE_TOPIC = "claim-update-response-event";
    private static final String CLAIM_SAGA_ORCHESTRATION_REQUEST_TOPIC = "claim-saga-orchestration-request-event";
    private static final String CLAIM_SAGA_STEP_COMMAND_TOPIC = "claim-saga-step-command-event";
    private static final String CLAIM_SAGA_STEP_RESULT_TOPIC = "claim-saga-step-result-event";
    private static final String CLAIM_SAGA_ORCHESTRATION_RESULT_TOPIC = "claim-saga-orchestration-result-event";

    private static final String CLAIM_UPDATE_REQUEST_DLT = CLAIM_UPDATE_REQUEST_TOPIC + ".DLT";
    private static final String CLAIM_UPDATE_RESPONSE_DLT = CLAIM_UPDATE_RESPONSE_TOPIC + ".DLT";
    private static final String CLAIM_SAGA_ORCHESTRATION_REQUEST_DLT = CLAIM_SAGA_ORCHESTRATION_REQUEST_TOPIC + ".DLT";
    private static final String CLAIM_SAGA_STEP_COMMAND_DLT = CLAIM_SAGA_STEP_COMMAND_TOPIC + ".DLT";
    private static final String CLAIM_SAGA_STEP_RESULT_DLT = CLAIM_SAGA_STEP_RESULT_TOPIC + ".DLT";
    private static final String CLAIM_SAGA_ORCHESTRATION_RESULT_DLT = CLAIM_SAGA_ORCHESTRATION_RESULT_TOPIC + ".DLT";

    @Value("${outbox.topics.partitions:3}")
    private int topicPartitions;

    @Value("${outbox.topics.replication-factor:1}")
    private short topicReplicationFactor;

    @Value("${outbox.topics.min-in-sync-replicas:1}")
    private String minInSyncReplicas;

    @Bean
    public NewTopic claimUpdateRequestTopic() {
        return TopicBuilder.name(CLAIM_UPDATE_REQUEST_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimUpdateResponseTopic() {
        return TopicBuilder.name(CLAIM_UPDATE_RESPONSE_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaOrchestrationRequestTopic() {
        return TopicBuilder.name(CLAIM_SAGA_ORCHESTRATION_REQUEST_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaStepCommandTopic() {
        return TopicBuilder.name(CLAIM_SAGA_STEP_COMMAND_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaStepResultTopic() {
        return TopicBuilder.name(CLAIM_SAGA_STEP_RESULT_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaOrchestrationResultTopic() {
        return TopicBuilder.name(CLAIM_SAGA_ORCHESTRATION_RESULT_TOPIC)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    // Dead Letter Topics
    @Bean
    public NewTopic claimUpdateRequestDLT() {
        return TopicBuilder.name(CLAIM_UPDATE_REQUEST_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimUpdateResponseDLT() {
        return TopicBuilder.name(CLAIM_UPDATE_RESPONSE_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaOrchestrationRequestDLT() {
        return TopicBuilder.name(CLAIM_SAGA_ORCHESTRATION_REQUEST_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaStepCommandDLT() {
        return TopicBuilder.name(CLAIM_SAGA_STEP_COMMAND_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaStepResultDLT() {
        return TopicBuilder.name(CLAIM_SAGA_STEP_RESULT_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }

    @Bean
    public NewTopic claimSagaOrchestrationResultDLT() {
        return TopicBuilder.name(CLAIM_SAGA_ORCHESTRATION_RESULT_DLT)
                .partitions(topicPartitions)
                .replicas(topicReplicationFactor)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
                .build();
    }
}
