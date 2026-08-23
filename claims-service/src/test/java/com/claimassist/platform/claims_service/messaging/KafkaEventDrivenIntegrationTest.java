package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.common_lib.messaging.DeadLetterMetadata;
import com.claimassist.platform.common_lib.messaging.SharedKafkaAutoConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 - end-to-end reliability of the real producer / consumer / error-handler beans
 * from {@link SharedKafkaAutoConfiguration} against a disposable Testcontainers Kafka broker.
 * <p>
 * Covers: producer -> topic delivery, same-key ordering & partition affinity, and
 * retry-exhaustion routing to {@code <topic>.DLT} with {@link DeadLetterMetadata} headers.
 * <p>
 * Gated by {@code @Testcontainers(disabledWithoutDocker = true)}: reported as SKIPPED when
 * no Docker environment exists, and runs for real in Docker-enabled CI.
 */
@SpringBootTest(classes = {
        SharedKafkaAutoConfiguration.class,
        KafkaEventDrivenIntegrationTest.ListenerConfig.class,
        KafkaEventDrivenIntegrationTest.PoisonListener.class
})
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("testcontainers")
class KafkaEventDrivenIntegrationTest {

    @Container
    static KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void producerDeliversRecordToTopic() throws Exception {
        kafkaTemplate.send("it-delivery", "claim-1", "{\"v\":1}").get(15, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> records =
                consume("it-delivery", Duration.ofSeconds(15), 1);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo("claim-1");
        assertThat(records.get(0).value()).isEqualTo("{\"v\":1}");
    }

    @Test
    void sameKeyRecordsSharePartitionAndPreserveOrder() throws Exception {
        kafkaTemplate.send("it-order", "claim-100", "a").get(15, TimeUnit.SECONDS);
        kafkaTemplate.send("it-order", "claim-100", "b").get(15, TimeUnit.SECONDS);
        kafkaTemplate.send("it-order", "claim-100", "c").get(15, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> records =
                consume("it-order", Duration.ofSeconds(15), 3);

        assertThat(records).hasSize(3);
        int partition = records.get(0).partition();
        assertThat(records).allMatch(r -> r.partition() == partition);
        assertThat(records).extracting(ConsumerRecord::value).containsExactly("a", "b", "c");
    }

    @Test
    void exhaustedRetriesRouteToDltWithMetadataHeaders() throws Exception {
        kafkaTemplate.send("it-dlt-input", "claim-7", "poison").get(15, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> dltRecords =
                consume("it-dlt-input.DLT", Duration.ofSeconds(45), 1);

        assertThat(dltRecords).as("poison message must be recovered to the DLT").hasSize(1);
        ConsumerRecord<String, String> dlt = dltRecords.get(0);
        assertThat(headerValue(dlt, DeadLetterMetadata.ORIGINAL_TOPIC_HEADER))
                .isEqualTo("it-dlt-input");
        assertThat(headerValue(dlt, DeadLetterMetadata.RETRY_COUNT_HEADER)).isEqualTo("1");
        assertThat(headerValue(dlt, DeadLetterMetadata.EXCEPTION_CLASS_HEADER))
                .contains("IllegalStateException");
        assertThat(headerValue(dlt, DeadLetterMetadata.ORIGINAL_PARTITION_HEADER)).isNotEmpty();
        assertThat(headerValue(dlt, DeadLetterMetadata.ORIGINAL_OFFSET_HEADER)).isNotEmpty();
    }

    private List<ConsumerRecord<String, String>> consume(String topic, Duration timeout, int expected) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("it", "it", "client")) {
            consumer.subscribe(Collections.singleton(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && records.size() < expected) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    records.add(r);
                }
            }
            return records;
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }

    /** Minimal Kafka wiring - topic beans are not required since topics are auto-created. */
    @Configuration
    @EnableKafka
    static class ListenerConfig {
    }

    /** Listener that fails on the poison payload so the real error handler retries -> DLT. */
    @Component
    static class PoisonListener {

        @KafkaListener(topics = "it-dlt-input", groupId = "it-dlt-group",
                containerFactory = "stringKafkaListenerContainerFactory")
        public void consume(@Header(KafkaHeaders.RECEIVED_TOPIC) String topic, String payload) {
            if ("poison".equals(payload)) {
                throw new IllegalStateException("poison message rejected for " + topic);
            }
        }
    }
}