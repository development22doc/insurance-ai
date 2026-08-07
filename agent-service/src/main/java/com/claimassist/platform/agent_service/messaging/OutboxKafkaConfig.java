package com.claimassist.platform.agent_service.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/** See claims-service's identically-named class for the full rationale. */
@Configuration
public class OutboxKafkaConfig {

    private static final String CLAIM_UPDATE_REQUEST_TOPIC = "claim-update-request-event";
    private static final String CLAIM_UPDATE_RESPONSE_TOPIC = "claim-update-response-event";
    private static final String CLAIM_UPDATE_REQUEST_DLT = CLAIM_UPDATE_REQUEST_TOPIC + ".DLT";
    private static final String CLAIM_UPDATE_RESPONSE_DLT = CLAIM_UPDATE_RESPONSE_TOPIC + ".DLT";

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.application.name:agent-service}")
    private String applicationName;

    @Value("${spring.kafka.producer.acks:all}")
    private String producerAcks;

    @Value("${spring.kafka.producer.retries:2147483647}")
    private int producerRetries;

    @Value("${spring.kafka.producer.batch-size:16384}")
    private int producerBatchSize;

    @Value("${spring.kafka.producer.linger-ms:10}")
    private int producerLingerMs;

    @Value("${spring.kafka.producer.properties.compression.type:none}")
    private String compressionType;

    @Value("${spring.kafka.producer.properties.delivery.timeout.ms:120000}")
    private int deliveryTimeoutMs;

    @Value("${spring.kafka.producer.properties.request.timeout.ms:30000}")
    private int requestTimeoutMs;

    @Value("${spring.kafka.producer.properties.retry.backoff.ms:1000}")
    private int retryBackoffMs;

    @Value("${outbox.topics.partitions:3}")
    private int topicPartitions;

    @Value("${outbox.topics.replication-factor:1}")
    private short topicReplicationFactor;

    @Value("${outbox.topics.min-in-sync-replicas:1}")
    private String minInSyncReplicas;

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.CLIENT_ID_CONFIG, applicationName + "-outbox-producer");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, producerAcks);
        config.put(ProducerConfig.RETRIES_CONFIG, producerRetries);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, producerBatchSize);
        config.put(ProducerConfig.LINGER_MS_CONFIG, producerLingerMs);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }

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

    @Bean
    public ConsumerFactory<String, String> outboxConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public CommonErrorHandler outboxConsumerErrorHandler(KafkaTemplate<String, String> outboxKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(outboxKafkaTemplate);
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(4);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory(
            ConsumerFactory<String, String> outboxConsumerFactory,
            CommonErrorHandler outboxConsumerErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(outboxConsumerFactory);
        factory.setCommonErrorHandler(outboxConsumerErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleEventInterval(60000L);
        return factory;
    }
}
