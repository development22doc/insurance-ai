package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration;
import com.claimassist.platform.policy_service.dto.PolicyCreateRequestDto;
import com.claimassist.platform.policy_service.dto.PolicyCreateResponseDto;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.service.PolicyCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPolicyController.class)
@Import({SharedExceptionAutoConfiguration.class, InternalPolicyControllerCreateTest.TestExceptionAdvice.class})
@org.springframework.security.test.context.support.WithMockUser
class InternalPolicyControllerCreateTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    com.claimassist.platform.policy_service.service.PolicyCoverageQueryService coverageService;

    @MockBean
    com.claimassist.platform.policy_service.service.PolicyLookupService policyLookupService;

    @MockBean
    com.claimassist.platform.policy_service.security.InternalRequestIdentity internalRequestIdentity;

    @MockBean
    PolicyCreationService policyCreationService;

    @MockBean
    com.claimassist.platform.policy_service.service.IdempotencyService idempotencyService;

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class TestExceptionAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(BadRequestException.class)
        public org.springframework.http.ResponseEntity<com.claimassist.platform.common_lib.error.ApiError> handle(BadRequestException ex) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                    .body(new com.claimassist.platform.common_lib.error.ApiError(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage()));
        }
    }

    @Test
    void createPolicy_missingIdempotencyKey_returns400() throws Exception {
        String body = "{\"customerId\":1,\"productCode\":\"P\",\"planCode\":\"PL\",\"coverageCode\":\"C\"}";

        mockMvc.perform(post("/internal/v1/policies")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPolicy_withValidRequest_callsServiceAndReturnsDto() throws Exception {
        when(internalRequestIdentity.resolveCallingUserId(any())).thenReturn(77L);

        Policy p = new Policy();
        p.setId(123L);
        p.setPolicyNumber("POL-123");
        p.setStatus("PENDING_PAYMENT");
        p.setStripePaymentIntentId("pi_abc");

        when(policyCreationService.createPolicy(any(), eq("77"), eq("idem-1"))).thenReturn(p);

        String body = "{\"customerId\":1,\"productCode\":\"P\",\"planCode\":\"PL\",\"coverageCode\":\"C\",\"effectiveDate\":\"2026-01-01T00:00:00Z\"}";

        mockMvc.perform(post("/internal/v1/policies")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Idempotency-Key", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // verify idempotency key passed to service
        verify(policyCreationService).createPolicy(any(), eq("77"), eq("idem-1"));
    }

}
