package com.claimassist.platform.common_lib.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Builds the Kafka message headers attached to a Dead Letter Topic (DLT) record so a
 * failed event is diagnosable and safely replayable.
 * <p>
 * The default Spring Kafka {@code DeadLetterPublishingRecoverer} does not attach any of
 * these details, so an operator seeing a message on a {@code .DLT} topic cannot tell which
 * partition/offset it came from, why it failed, or how many times it was retried. These
 * headers close that gap.
 * <p>
 * Purely additive - existing DLT consumers (including the default {@code <topic>.DLT}
 * destination used by {@link SharedKafkaAutoConfiguration}) are unaffected.
 * <p>
 * Header values deliberately exclude payloads and authentication material: they carry only
 * routing/failure metadata required for diagnosis.
 */
public final class DeadLetterMetadata {

    public static final String ORIGINAL_TOPIC_HEADER = "dlt.original.topic";
    public static final String ORIGINAL_PARTITION_HEADER = "dlt.original.partition";
    public static final String ORIGINAL_OFFSET_HEADER = "dlt.original.offset";
    public static final String EXCEPTION_CLASS_HEADER = "dlt.exception.class";
    public static final String EXCEPTION_MESSAGE_HEADER = "dlt.exception.message";
    public static final String FAILURE_TIMESTAMP_HEADER = "dlt.failure.timestamp";
    public static final String RETRY_COUNT_HEADER = "dlt.retry.count";

    private DeadLetterMetadata() {
    }

    /**
     * Attaches DLT metadata headers to {@code headers} describing the failed {@code record}.
     * The retry count is derived from any existing {@link #RETRY_COUNT_HEADER} on the original
     * record (so consecutive redeliveries accumulate) or defaults to {@code 1}.
     *
     * @param record    the original failed record (source of topic/partition/offset)
     * @param exception the failure that exhausted retries (may be {@code null})
     * @param headers   the header set of the DLT record to populate
     */
    public static void addHeaders(ConsumerRecord<?, ?> record, Exception exception, Headers headers) {
        if (record == null) {
            return;
        }
        headers.add(ORIGINAL_TOPIC_HEADER, toBytes(record.topic()));
        headers.add(ORIGINAL_PARTITION_HEADER, toBytes(Integer.toString(record.partition())));
        headers.add(ORIGINAL_OFFSET_HEADER, toBytes(Long.toString(record.offset())));
        headers.add(FAILURE_TIMESTAMP_HEADER, toBytes(Instant.now().toString()));

        if (exception != null) {
            headers.add(EXCEPTION_CLASS_HEADER, toBytes(exception.getClass().getName()));
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                headers.add(EXCEPTION_MESSAGE_HEADER, toBytes(message));
            }
        }

        headers.add(RETRY_COUNT_HEADER, toBytes(Integer.toString(incrementRetryCount(record))));
    }

    private static int incrementRetryCount(ConsumerRecord<?, ?> record) {
        Header existing = record.headers().lastHeader(RETRY_COUNT_HEADER);
        if (existing != null) {
            try {
                return Integer.parseInt(new String(existing.value(), StandardCharsets.UTF_8)) + 1;
            } catch (NumberFormatException e) {
                return 2;
            }
        }
        return 1;
    }

    private static byte[] toBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
