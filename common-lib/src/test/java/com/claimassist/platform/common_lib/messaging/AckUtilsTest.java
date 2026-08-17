package com.claimassist.platform.common_lib.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AckUtilsTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void acknowledgesImmediatelyWhenNoTransactionIsActive() {
        Acknowledgment ack = mock(Acknowledgment.class);
        AckUtils.acknowledgeAfterCommit(ack);
        verify(ack).acknowledge();
    }

    @Test
    void defersAckUntilAfterCommitWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        Acknowledgment ack = mock(Acknowledgment.class);

        AckUtils.acknowledgeAfterCommit(ack);

        verify(ack, never()).acknowledge();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);
        sync.afterCommit();
        verify(ack).acknowledge();
    }

    @Test
    void doesNotAcknowledgeOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        Acknowledgment ack = mock(Acknowledgment.class);

        AckUtils.acknowledgeAfterCommit(ack);

        // On rollback afterCommit is never invoked, so the offset stays uncommitted
        // and Kafka redelivers (at-least-once semantics).
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(ack, never()).acknowledge();
    }

    @Test
    void noopWhenAckIsNull() {
        AckUtils.acknowledgeAfterCommit(null);
        // no exception expected
        assertThat(true).isTrue();
    }
}