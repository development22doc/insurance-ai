package com.claimassist.platform.common_lib.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SharedKafkaAutoConfigurationTest {

    private SharedKafkaAutoConfiguration configureDefaults() {
        SharedKafkaAutoConfiguration config = new SharedKafkaAutoConfiguration();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(config, "applicationName", "claims-service");
        ReflectionTestUtils.setField(config, "producerAcks", "all");
        ReflectionTestUtils.setField(config, "producerRetries", 10);
        ReflectionTestUtils.setField(config, "producerBatchSize", 16384);
        ReflectionTestUtils.setField(config, "producerLingerMs", 10);
        ReflectionTestUtils.setField(config, "compressionType", "lz4");
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 120000);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", 30000);
        ReflectionTestUtils.setField(config, "retryBackoffMs", 1000);
        return config;
    }

    @Test
    void outboxProducerFactory_buildsIdempotentStringProducerConfig() {
        SharedKafkaAutoConfiguration config = configureDefaults();

        ProducerFactory<String, String> factory = config.outboxProducerFactory();

        Map<String, Object> props = ((DefaultKafkaProducerFactory<String, String>) factory).getConfigurationProperties();
        assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka:9092");
        assertThat(props.get(ProducerConfig.CLIENT_ID_CONFIG)).isEqualTo("claims-service-outbox-producer");
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(props.get(ProducerConfig.COMPRESSION_TYPE_CONFIG)).isEqualTo("lz4");
    }

    @Test
    void outboxConsumerFactory_buildsManualCommitStringConsumerConfig() {
        SharedKafkaAutoConfiguration config = configureDefaults();

        ConsumerFactory<String, String> factory = config.outboxConsumerFactory();

        Map<String, Object> props = ((DefaultKafkaConsumerFactory<String, String>) factory).getConfigurationProperties();
        assertThat(props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka:9092");
        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    void outboxKafkaTemplate_wiresProducerFactory() {
        SharedKafkaAutoConfiguration config = configureDefaults();
        ProducerFactory<String, String> factory = config.outboxProducerFactory();

        KafkaTemplate<String, String> template = config.outboxKafkaTemplate(factory);

        assertThat(template).isNotNull();
    }

    @Test
    void outboxConsumerErrorHandler_andContainerFactory_areConfigured() {
        SharedKafkaAutoConfiguration config = configureDefaults();
        ProducerFactory<String, String> factory = config.outboxProducerFactory();
        KafkaTemplate<String, String> template = config.outboxKafkaTemplate(factory);
        ConsumerFactory<String, String> consumerFactory = config.outboxConsumerFactory();

        CommonErrorHandler errorHandler = config.outboxConsumerErrorHandler(template);
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
                config.stringKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
        assertThat(containerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL);
    }
}