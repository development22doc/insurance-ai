package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.ProcessedStripeEvent;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.ProcessedStripeEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StripeWebhookControllerUnitTest {

    PolicyRepository policyRepository = mock(PolicyRepository.class);
    ProcessedStripeEventRepository processedRepo = mock(ProcessedStripeEventRepository.class);
    CacheManager cacheManager = mock(CacheManager.class);
    org.springframework.cache.Cache policyCoverageCache = mock(org.springframework.cache.Cache.class);

    StripeWebhookController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new StripeWebhookController(policyRepository, processedRepo, cacheManager);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_COVERAGE_CACHE)).thenReturn(policyCoverageCache);
        // inject webhook signing secret used by controller (mimic @Value)
        java.lang.reflect.Field secretField = StripeWebhookController.class.getDeclaredField("webhookSigningSecret");
        secretField.setAccessible(true);
        secretField.set(controller, "test_secret");
    }

    @Test
    void handle_validPaymentIntentSucceeded_activatesPolicy_and_recordsEvent() throws Exception {
        // Mock Event and PaymentIntent
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_1");
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");

        PaymentIntent mockPi = mock(PaymentIntent.class);
        when(mockPi.getId()).thenReturn("pi_1");
        when(mockPi.getMetadata()).thenReturn(Map.of("policy_number", "POL-42-000001"));

        EventDataObjectDeserializer des = mock(EventDataObjectDeserializer.class);
        when(des.getObject()).thenReturn(Optional.of(mockPi));
        when(mockEvent.getDataObjectDeserializer()).thenReturn(des);

        // Mock Webhook.constructEvent static to return our mockEvent
        try (MockedStatic<Webhook> ws = Mockito.mockStatic(Webhook.class)) {
            ws.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

            Policy p = new Policy();
            p.setPolicyNumber("POL-42-000001");
            p.setStatus("PENDING_PAYMENT");
            p.setStripePaymentIntentId("pi_1");

            when(policyRepository.findByPolicyNumber("POL-42-000001")).thenReturn(Optional.of(p));
            when(processedRepo.save(any(ProcessedStripeEvent.class))).thenReturn(new ProcessedStripeEvent("evt_1", "payment_intent.succeeded", Instant.now(), null));

            var resp = controller.handle("{}", "t=1,v1=signature");
            assertThat(resp.getStatusCodeValue()).isEqualTo(200);

            // verify policy was saved as ACTIVE
            verify(policyRepository, times(1)).save(argThat(policy -> "ACTIVE".equals(policy.getStatus())));
            verify(processedRepo, times(1)).save(any(ProcessedStripeEvent.class));
        }
    }

    @Test
    void handle_validPaymentIntentSucceeded_invalidatesPolicyCoverageCache() throws Exception {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_2");
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");

        PaymentIntent mockPi = mock(PaymentIntent.class);
        when(mockPi.getId()).thenReturn("pi_2");
        when(mockPi.getMetadata()).thenReturn(Map.of("policy_number", "POL-42-000002"));

        EventDataObjectDeserializer des = mock(EventDataObjectDeserializer.class);
        when(des.getObject()).thenReturn(Optional.of(mockPi));
        when(mockEvent.getDataObjectDeserializer()).thenReturn(des);

        try (MockedStatic<Webhook> ws = Mockito.mockStatic(Webhook.class)) {
            ws.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

            Policy p = new Policy();
            p.setId(42L);
            p.setPolicyNumber("POL-42-000002");
            p.setStatus("PENDING_PAYMENT");
            p.setStripePaymentIntentId("pi_2");

            when(policyRepository.findByPolicyNumber("POL-42-000002")).thenReturn(Optional.of(p));
            when(processedRepo.save(any(ProcessedStripeEvent.class))).thenReturn(new ProcessedStripeEvent("evt_2", "payment_intent.succeeded", Instant.now(), null));

            var resp = controller.handle("{}", "t=1,v1=signature");
            assertThat(resp.getStatusCodeValue()).isEqualTo(200);
            verify(policyCoverageCache).evict(42L);
        }
    }

    @Test
    void handle_nonPaymentIntentEvent_doesNotEvictCoverageCache() throws Exception {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn("evt_3");
        when(mockEvent.getType()).thenReturn("payment_intent.requires_action");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(mock(EventDataObjectDeserializer.class));

        try (MockedStatic<Webhook> ws = Mockito.mockStatic(Webhook.class)) {
            ws.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);
            when(processedRepo.save(any(ProcessedStripeEvent.class))).thenReturn(new ProcessedStripeEvent("evt_3", "payment_intent.requires_action", Instant.now(), null));

            var resp = controller.handle("{}", "t=1,v1=signature");
            assertThat(resp.getStatusCodeValue()).isEqualTo(200);
            verify(policyCoverageCache, never()).evict(anyLong());
        }
    }

    @Test
    void handle_invalidSignature_returns400() throws Exception {
        try (MockedStatic<Webhook> ws = Mockito.mockStatic(Webhook.class)) {
            ws.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenThrow(SignatureVerificationException.class);

            var resp = controller.handle("{}", "bad-signature");
            assertThat(resp.getStatusCodeValue()).isEqualTo(400);
            verifyNoInteractions(policyRepository);
            verifyNoInteractions(processedRepo);
        }
    }
}
