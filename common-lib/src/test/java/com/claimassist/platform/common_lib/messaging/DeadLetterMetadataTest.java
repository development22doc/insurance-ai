package com.claimassist.platform.common_lib.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterMetadataTest {

    @Test
    void addsRoutingAndFailureMetadataFromFailedRecord() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("claim-update-request-event", 2, 99L, "claim-7", "{\"sagaId\":\"s1\"}");
        Exception failure = new IllegalStateException("boom");

        RecordHeaders headers = new RecordHeaders();
        DeadLetterMetadata.addHeaders(record, failure, headers);

        assertThat(header(headers, DeadLetterMetadata.ORIGINAL_TOPIC_HEADER))
                .isEqualTo("claim-update-request-event");
        assertThat(header(headers, DeadLetterMetadata.ORIGINAL_PARTITION_HEADER)).isEqualTo("2");
        assertThat(header(headers, DeadLetterMetadata.ORIGINAL_OFFSET_HEADER)).isEqualTo("99");
        assertThat(header(headers, DeadLetterMetadata.EXCEPTION_CLASS_HEADER))
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(header(headers, DeadLetterMetadata.EXCEPTION_MESSAGE_HEADER)).isEqualTo("boom");
        assertThat(header(headers, DeadLetterMetadata.RETRY_COUNT_HEADER)).isEqualTo("1");
        assertThat(Instant.parse(header(headers, DeadLetterMetadata.FAILURE_TIMESTAMP_HEADER))).isNotNull();
    }

    @Test
    void defaultsRetryCountToOneWhenNoExistingHeader() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("t", 0, 1L, "k", "v");
        RecordHeaders headers = new RecordHeaders();
        DeadLetterMetadata.addHeaders(record, null, headers);
        assertThat(header(headers, DeadLetterMetadata.RETRY_COUNT_HEADER)).isEqualTo("1");
        assertThat(headers.iterator().hasNext()).isTrue();
    }

    @Test
    void incrementsRetryCountAcrossRedeliveries() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("t", 0, 1L, "k", "v");
        record.headers().add(DeadLetterMetadata.RETRY_COUNT_HEADER, "3".getBytes(StandardCharsets.UTF_8));

        RecordHeaders headers = new RecordHeaders();
        DeadLetterMetadata.addHeaders(record, null, headers);
        assertThat(header(headers, DeadLetterMetadata.RETRY_COUNT_HEADER)).isEqualTo("4");
    }

    @Test
    void omitsExceptionMessageHeaderWhenBlankButKeepsClass() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("t", 0, 1L, "k", "v");
        RecordHeaders headers = new RecordHeaders();
        DeadLetterMetadata.addHeaders(record, new IllegalArgumentException(" "), headers);
        assertThat(header(headers, DeadLetterMetadata.EXCEPTION_CLASS_HEADER))
                .isEqualTo("java.lang.IllegalArgumentException");
        assertThat(headers.lastHeader(DeadLetterMetadata.EXCEPTION_MESSAGE_HEADER)).isNull();
    }

    @Test
    void noopWhenRecordIsNull() {
        RecordHeaders headers = new RecordHeaders();
        DeadLetterMetadata.addHeaders(null, null, headers);
        assertThat(headers.iterator().hasNext()).isFalse();
    }

    private static String header(RecordHeaders headers, String name) {
        Header h = headers.lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}