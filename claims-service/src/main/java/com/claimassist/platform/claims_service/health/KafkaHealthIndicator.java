package com.claimassist.platform.claims_service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Health indicator for Kafka connectivity and cluster health.
 */
@Component("kafkaHealth")
@RequiredArgsConstructor
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    @Override
    public Health health() {
        try {
            AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());

            try {
                // Attempt to list topics with a timeout to verify connectivity
                var topicsFuture = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
                topicsFuture.names().get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                return Health.up()
                        .withDetail("kafka", "Connected")
                        .withDetail("bootstrap-servers", kafkaAdmin.getConfigurationProperties()
                                .get("bootstrap.servers"))
                        .build();

            } catch (TimeoutException | InterruptedException | ExecutionException e) {
                log.error("Kafka health check timeout/error", e);
                return Health.down()
                        .withException(e)
                        .withDetail("issue", "Failed to connect to Kafka cluster")
                        .build();
            } finally {
                adminClient.close();
            }
        } catch (Exception e) {
            log.error("Error creating Kafka admin client", e);
            return Health.down()
                    .withException(e)
                    .withDetail("issue", "Failed to create Kafka admin client")
                    .build();
        }
    }
}

