package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaCompensationHandlerTest {

    @Mock
    private ClaimCommandService commandService;

    @Mock
    private SagaOutboxPublisher outboxPublisher;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private SagaCompensationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SagaCompensationHandler(commandService, outboxPublisher, outboxEventRepository, objectMapper);
        lenient().when(outboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
    }

    @Test
    void paymentFailureQueuesPaymentCompensation() {
        handler.executeCompensation("s1", SagaStepType.PAYMENT, 100L, "payment declined");

        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq("claim-payment-compensation"), eq("claim-100"), anyString());
    }

    @Test
    void notificationFailureQueuesNotificationCompensation() {
        handler.executeCompensation("s1", SagaStepType.NOTIFICATION, 100L, "notification failed");

        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq("claim-notification-compensation"), eq("claim-100"), anyString());
    }

    @Test
    void noCompensationForCreateClaimStep() {
        handler.executeCompensation("s1", SagaStepType.CREATE_CLAIM, 100L, "reason");

        verify(outboxPublisher, never()).enqueueIfAbsent(
                anyString(), anyString(), eq("claim-payment-compensation"), anyString(), anyString());
    }

    @Test
    void compensationIsIdempotentAtPublisherLevel() {
        // enqueueIfAbsent returning false means the event was already queued - no duplicate
        when(outboxPublisher.enqueueIfAbsent(anyString(), anyString(), eq("claim-payment-compensation"), anyString(), anyString()))
                .thenReturn(false);

        handler.executeCompensation("s1", SagaStepType.PAYMENT, 100L, "payment declined");

        verify(outboxPublisher, times(1)).enqueueIfAbsent(
                anyString(), anyString(), eq("claim-payment-compensation"), anyString(), anyString());
    }

    @Test
    void compensationFailureIsRecordedOnFailureTopic() {
        // First call (compensatePayment) throws; the recordCompensationFailure call then succeeds
        when(outboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("serialize failed"))
                .thenReturn(true);

        handler.executeCompensation("s1", SagaStepType.PAYMENT, 100L, "payment declined");

        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq("saga-compensation-failures"), eq("saga-s1"), anyString());
    }
}