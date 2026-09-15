package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.policy_service.service.PurchaseService;
import com.claimassist.platform.policy_service.service.StripePaymentGateway;
import com.claimassist.platform.policy_service.repository.PaymentEventRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripePaymentGateway stripePaymentGateway;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private PurchaseService purchaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(stripePaymentGateway, purchaseRepository, paymentEventRepository, purchaseService))
                .setControllerAdvice(new com.claimassist.platform.common_lib.error.GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void handleStripeWebhook_emptyPayload_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe").content("")
                        .header("Stripe-Signature", "t=123,v1=test"))
                .andExpect(status().isBadRequest());
    }
}
