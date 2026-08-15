package com.claimassist.platform.common_lib.messaging;

import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Shared helper for Kafka manual-acknowledgement ordering.
 * <p>
 * When a {@code @KafkaListener} is also {@code @Transactional}, the container
 * factory is configured with {@code AckMode.MANUAL} and {@code enable.auto.commit=false}.
 * A naive {@code ack.acknowledge()} called from inside the listener body runs
 * BEFORE the surrounding transaction commits (the DB commit happens when the
 * proxied method returns). If the commit then fails, the offset is already
 * acknowledged and the message is lost.
 * <p>
 * {@link #acknowledgeAfterCommit(Acknowledgment)} registers a transaction
 * synchronization that acknowledges only after a successful commit. On rollback
 * the synchronization is not invoked, so the offset stays uncommitted and Kafka
 * redelivers the message according to the existing retry/DLT semantics.
 * <p>
 * If no transaction is active (non-transactional listener), it acknowledges
 * immediately.
 */
public final class AckUtils {

    private AckUtils() {
    }

    /**
     * Acknowledge the Kafka offset only after the surrounding transaction commits.
     *
     * @param ack the {@link Acknowledgment} provided by the listener container
     */
    public static void acknowledgeAfterCommit(Acknowledgment ack) {
        if (ack == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ack.acknowledge();
                }
            });
        } else {
            ack.acknowledge();
        }
    }
}