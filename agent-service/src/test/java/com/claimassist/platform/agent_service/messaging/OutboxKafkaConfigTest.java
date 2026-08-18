package com.claimassist.platform.agent_service.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxKafkaConfigTest {

    private OutboxKafkaConfig config() {
        OutboxKafkaConfig c = new OutboxKafkaConfig();
        ReflectionTestUtils.setField(c, "topicPartitions", 3);
        ReflectionTestUtils.setField(c, "topicReplicationFactor", (short) 1);
        ReflectionTestUtils.setField(c, "minInSyncReplicas", "1");
        return c;
    }

    @Test
    void definesRequestAndResponseTopicsWithConfiguredPartitions() {
        OutboxKafkaConfig config = config();
        NewTopic request = config.claimUpdateRequestTopic();
        NewTopic response = config.claimUpdateResponseTopic();

        assertThat(request.name()).isEqualTo("claim-update-request-event");
        assertThat(request.numPartitions()).isEqualTo(3);
        assertThat(request.replicationFactor()).isEqualTo((short) 1);
        assertThat(request.configs()).containsEntry(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
        assertThat(response.name()).isEqualTo("claim-update-response-event");
    }

    @Test
    void definesDLTTopics() {
        OutboxKafkaConfig config = config();
        NewTopic requestDlt = config.claimUpdateRequestDLT();
        NewTopic responseDlt = config.claimUpdateResponseDLT();

        assertThat(requestDlt.name()).isEqualTo("claim-update-request-event.DLT");
        assertThat(responseDlt.name()).isEqualTo("claim-update-response-event.DLT");
        assertThat(requestDlt.configs()).containsEntry(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
    }
}
