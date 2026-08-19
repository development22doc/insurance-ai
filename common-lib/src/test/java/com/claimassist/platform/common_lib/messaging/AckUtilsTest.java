package com.claimassist.platform.common_lib.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AckUtilsTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void acknowledgeAfterCommit_NullAck_ShouldBeNoOp() {
        assertThatCode(() -> AckUtils.acknowledgeAfterCommit(null)).doesNotThrowAnyException();
    }

    @Test
    void acknowledgeAfterCommit_NoActiveTransaction_ShouldAcknowledgeImmediately() {
        Acknowledgment ack = mock(Acknowledgment.class);

        AckUtils.acknowledgeAfterCommit(ack);

        verify(ack).acknowledge();
    }

    @Test
    void acknowledgeAfterCommit_ActiveTransaction_ShouldNotAcknowledgeBeforeCommit() {
        Acknowledgment ack = mock(Acknowledgment.class);
        TransactionSynchronizationManager.initSynchronization();

        AckUtils.acknowledgeAfterCommit(ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    void acknowledgeAfterCommit_ActiveTransaction_ShouldAcknowledgeAfterCommitCompletes() {
        Acknowledgment ack = mock(Acknowledgment.class);
        TransactionSynchronizationManager.initSynchronization();

        AckUtils.acknowledgeAfterCommit(ack);
        verify(ack, never()).acknowledge();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(ack).acknowledge();
    }

    @Test
    void acknowledgeAfterCommit_Rollback_ShouldNeverAcknowledge() {
        Acknowledgment ack = mock(Acknowledgment.class);
        TransactionSynchronizationManager.initSynchronization();

        AckUtils.acknowledgeAfterCommit(ack);

        // Simulate rollback: the synchronization's afterCommit must not be invoked, and
        // the offset is left unacknowledged so Kafka redelivers per retry/DLT semantics.
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();
        verify(ack, never()).acknowledge();
    }
}