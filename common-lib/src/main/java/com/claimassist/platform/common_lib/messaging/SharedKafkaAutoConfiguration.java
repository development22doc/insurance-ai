package com.claimassist.platform.common_lib.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Kafka configuration for the Transactional Outbox pattern.
 * <p>
 * Provides common producer and consumer configuration beans used across
 * claims-service and agent-service. This eliminates duplication while
 * allowing each service to define its own topic-specific beans.
 * <p>
 * This configuration includes:
 * - ProducerFactory with String/String serializers
 * - KafkaTemplate for outbox event publishing
 * - ConsumerFactory with String/String deserializers
 * - CommonErrorHandler with DeadLetterPublishingRecoverer
 * - ConcurrentKafkaListenerContainerFactory for listener setup
 * <p>
 * All configuration values come from Spring properties with sensible defaults,
 * ensuring services can override them via config-repo or environment variables.
 * <p>
 * Note: This configuration does NOT define topic beans. Each service must
 * define its own topic beans (claim-update, saga topics, etc.) in its
 * service-specific OutboxKafkaConfig class.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class SharedKafkaAutoConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.application.name:service}")
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

    /**
     * ProducerFactory for outbox event publishing.
     * <p>
     * Configured with String/String serializers to avoid double-encoding
     * of already-serialized JSON payloads. Idempotence is enabled for
     * exactly-once semantics.
     * <p>
     * Client ID is service-specific based on spring.application.name.
     *
     * @return Configured ProducerFactory bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "outboxProducerFactory")
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

    /**
     * KafkaTemplate for outbox event publishing.
     * <p>
     * Uses the outboxProducerFactory for all outbox event operations.
     *
     * @param outboxProducerFactory The configured producer factory
     * @return Configured KafkaTemplate bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }

    /**
     * ConsumerFactory for outbox event consumption.
     * <p>
     * Configured with String/String deserializers and manual offset commit
     * for precise control over event processing.
     *
     * @return Configured ConsumerFactory bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "outboxConsumerFactory")
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

    /**
     * CommonErrorHandler for outbox event processing errors.
     * <p>
     * Publishes failed messages to the {@code <topic>.DLT} dead-letter topic with
     * {@link DeadLetterMetadata} headers (original topic/partition/offset, exception
     * class/reason, failure timestamp and cumulative retry count) so a DLT record is
     * diagnosable and safely replayable. Implements exponential backoff with 4 attempts
     * before giving up.
     *
     * @param outboxKafkaTemplate The configured KafkaTemplate for DLT publishing
     * @return Configured CommonErrorHandler bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "outboxConsumerErrorHandler")
    public CommonErrorHandler outboxConsumerErrorHandler(KafkaTemplate<String, String> outboxKafkaTemplate) {
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            @SuppressWarnings("unchecked")
            String key = (String) record.key();
            @SuppressWarnings("unchecked")
            String value = (String) record.value();
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(
                    record.topic() + ".DLT",
                    key,
                    value);
            DeadLetterMetadata.addHeaders(record, exception, dlqRecord.headers());
            outboxKafkaTemplate.send(dlqRecord);
        };
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(4);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * ConcurrentKafkaListenerContainerFactory for outbox event listeners.
     * <p>
     * Configured with manual ack mode and idle event interval for
     * robust event processing.
     *
     * @param outboxConsumerFactory The configured consumer factory
     * @param outboxConsumerErrorHandler The configured error handler
     * @return Configured ConcurrentKafkaListenerContainerFactory bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "stringKafkaListenerContainerFactory")
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
