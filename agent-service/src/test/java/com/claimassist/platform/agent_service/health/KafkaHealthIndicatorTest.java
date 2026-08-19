package com.claimassist.platform.agent_service.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    @Test
    void upWhenTopicsCanBeListed() {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of("bootstrap.servers", "localhost:9092"));
        AdminClient adminClient = mock(AdminClient.class);
        ListTopicsResult topicsResult = mock(ListTopicsResult.class);
        KafkaFuture<Set<String>> namesFuture = mock(KafkaFuture.class);
        when(topicsResult.names()).thenReturn(namesFuture);
        when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(topicsResult);

        try (MockedStatic<AdminClient> adminStatic = mockStatic(AdminClient.class)) {
            adminStatic.when(() -> AdminClient.create((java.util.Map<String, Object>) argThat(x -> x != null))).thenReturn(adminClient);

            Health health = new KafkaHealthIndicator(kafkaAdmin).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("kafka", "Connected");
        }
    }

    @Test
    void downWhenListingTopicsTimesOut() throws Exception {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of("bootstrap.servers", "localhost:9092"));
        AdminClient adminClient = mock(AdminClient.class);
        ListTopicsResult topicsResult = mock(ListTopicsResult.class);
        KafkaFuture<Set<String>> namesFuture = mock(KafkaFuture.class);
        when(topicsResult.names()).thenReturn(namesFuture);
        when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(topicsResult);
        when(namesFuture.get(any(Long.class), any(TimeUnit.class)))
                .thenThrow(new ExecutionException("timed out", new RuntimeException()));

        try (MockedStatic<AdminClient> adminStatic = mockStatic(AdminClient.class)) {
            adminStatic.when(() -> AdminClient.create((java.util.Map<String, Object>) argThat(x -> x != null))).thenReturn(adminClient);

            Health health = new KafkaHealthIndicator(kafkaAdmin).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("issue", "Failed to connect to Kafka cluster");
        }
    }

    @Test
    void downWhenAdminClientCreationFails() {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of("bootstrap.servers", "localhost:9092"));

        try (MockedStatic<AdminClient> adminStatic = mockStatic(AdminClient.class)) {
            adminStatic.when(() -> AdminClient.create((java.util.Map<String, Object>) argThat(x -> x != null)))
                    .thenThrow(new IllegalStateException("no brokers"));

            Health health = new KafkaHealthIndicator(kafkaAdmin).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("issue", "Failed to create Kafka admin client");
        }
    }
}
