package com.claimassist.platform.agent_service.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Agent-specific Kafka topic configuration for the outbox pattern.
 * <p>
 * Producer, consumer, and error handler configuration are provided by
 * SharedKafkaAutoConfiguration from common-lib. This class defines only
 * the agent-specific topic beans.
 * <p>
 * Agent service has a simpler topic set than claims service, reflecting
 * its simpler event-producing responsibilities (no saga orchestration).
 */
@Configuration
public class OutboxKafkaConfig {

    private static final String CLAIM_UPDATE_REQUEST_TOPIC = "claim-update-request-event";
    private static final String CLAIM_UPDATE_RESPONSE_TOPIC = "claim-update-response-event";
    private static final String CLAIM_UPDATE_REQUEST_DLT = CLAIM_UPDATE_REQUEST_TOPIC + ".DLT";
    private static final String CLAIM_UPDATE_RESPONSE_DLT = CLAIM_UPDATE_RESPONSE_TOPIC + ".DLT";

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
}
