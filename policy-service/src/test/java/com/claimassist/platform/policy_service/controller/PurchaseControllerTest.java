package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PurchaseInitiationRequest;
import com.claimassist.platform.policy_service.dto.PurchaseResponse;
import com.claimassist.platform.policy_service.dto.PurchaseStatusResponse;
import com.claimassist.platform.policy_service.exception.GlobalExceptionHandler;
import com.claimassist.platform.policy_service.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PurchaseControllerTest {

    @Mock
    private PurchaseService purchaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PurchaseController(purchaseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void purchase_returnsCreatedPendingPurchase() throws Exception {
        when(purchaseService.initiatePurchase(any(PurchaseInitiationRequest.class), eq("key-1")))
                .thenReturn(new PurchaseResponse(
                        7L,
                        42L,
                        22L,
                        11L,
                        "Comprehensive Auto",
                        "Auto Insurance",
                        "PENDING_PAYMENT",
                        50000L,
                        "INR",
                        "key-1",
                        Instant.parse("2026-09-14T00:00:00Z"),
                        Instant.parse("2026-09-14T00:00:01Z")
                ));

        mockMvc.perform(post("/api/v1/policies/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("{\"planId\":22}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.purchaseId").value(7));
    }

    @Test
    void getPurchaseStatus_returnsCurrentStatus() throws Exception {
        when(purchaseService.getPurchaseStatus(7L)).thenReturn(new PurchaseStatusResponse(
                7L,
                42L,
                22L,
                11L,
                "PENDING_PAYMENT",
                50000L,
                "INR",
                Instant.parse("2026-09-14T00:00:00Z"),
                Instant.parse("2026-09-14T00:00:01Z")
        ));

        mockMvc.perform(get("/api/v1/policies/purchases/7/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void purchase_notFound_returns404() throws Exception {
        when(purchaseService.initiatePurchase(any(PurchaseInitiationRequest.class), eq("missing")))
                .thenThrow(new ResourceNotFoundException("Plan", "9"));

        mockMvc.perform(post("/api/v1/policies/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "missing")
                        .content("{\"planId\":9}"))
                .andExpect(status().isNotFound());
    }
}
