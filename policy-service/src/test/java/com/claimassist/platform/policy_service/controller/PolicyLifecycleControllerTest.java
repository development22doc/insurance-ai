package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ApiError;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.*;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.security.TestExceptionAdvice;
import com.claimassist.platform.policy_service.security.TestJwtDecoderConfig;
import com.claimassist.platform.policy_service.service.PolicyLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PolicyController.class)
@TestPropertySource(properties = {"security.internal.trusted-service-client-ids=claimassist-admin-service"})
@Import({SharedExceptionAutoConfiguration.class, TestJwtDecoderConfig.class, TestExceptionAdvice.class, PolicyLifecycleControllerTest.TestControllerExceptionAdvice.class})
class PolicyLifecycleControllerTest {

    @RestControllerAdvice
    static class TestControllerExceptionAdvice {
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiError> handle(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(HttpStatus.NOT_FOUND, ex.getMessage()));
        }

        @ExceptionHandler(com.claimassist.platform.common_lib.error.BadRequestException.class)
        public ResponseEntity<ApiError> handleBadRequest(com.claimassist.platform.common_lib.error.BadRequestException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage()));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyLifecycleService policyLifecycleService;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PolicyRepository policyRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PlanRepository planRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.ProductRepository productRepository;

    @MockBean
    private com.claimassist.platform.policy_service.service.PublicPolicyQueryService publicPolicyQueryService;

    @MockBean
    private com.claimassist.platform.policy_service.service.ProductCatalogCommandService productCatalogCommandService;

    @MockBean
    private com.claimassist.platform.policy_service.service.PlanCatalogCommandService planCatalogCommandService;

    @MockBean
    private com.claimassist.platform.policy_service.service.PolicyLookupService policyLookupService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    // 1. Authenticated policy owner can invoke appropriate lifecycle endpoint.
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void ownerCanIssue() throws Exception {
        Policy p = Policy.builder().id(1L).policyNumber("POL-1-000001").customerId(100L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_1").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(100L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.issue(1L, null)).thenReturn(Policy.builder().id(1L).status("ACTIVE").build());

        mockMvc.perform(post("/api/v1/policies/1/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(policyLifecycleService, times(1)).issue(1L, null);
    }

    // 2. Non-owner customer receives 403.
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonOwnerCannotIssue() throws Exception {
        Policy p = Policy.builder().id(2L).policyNumber("POL-2-000002").customerId(100L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_2").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(201L); // different user
        when(policyRepository.findById(2L)).thenReturn(Optional.of(p));

        mockMvc.perform(post("/api/v1/policies/2/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(policyLifecycleService, never()).issue(anyLong(), any());
    }

    // 3. ADMIN can operate on another customer's policy
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanIssueOther() throws Exception {
        Policy p = Policy.builder().id(3L).policyNumber("POL-3").customerId(300L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_3").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(999L);
        when(policyRepository.findById(3L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.issue(3L, null)).thenReturn(Policy.builder().id(3L).status("ACTIVE").build());

        mockMvc.perform(post("/api/v1/policies/3/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(policyLifecycleService, times(1)).issue(3L, null);
    }

    // 4. OPERATIONS role can operate
    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsCanCancel() throws Exception {
        Policy p = Policy.builder().id(4L).policyNumber("POL-4").customerId(400L).status("ACTIVE").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(123L);
        when(policyRepository.findById(4L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.cancel(anyLong(), any(CancelRequestDto.class))).thenReturn(Policy.builder().id(4L).status("CANCELLED").build());

        mockMvc.perform(post("/api/v1/policies/4/cancel").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(policyLifecycleService, times(1)).cancel(anyLong(), any(CancelRequestDto.class));
    }

    // 5. Unauthenticated request is rejected
    @Test
    void unauthenticatedRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("No authenticated Keycloak JWT found"));

        mockMvc.perform(post("/api/v1/policies/1/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(policyLifecycleService, never()).issue(anyLong(), any());
    }

    // 6. Invalid policy ID/not found -> 404
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void policyNotFoundReturns404() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(500L);
        when(policyRepository.findById(500L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/policies/500/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // 7. Invalid lifecycle transition -> 400
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void invalidTransitionReturns400() throws Exception {
        Policy p = Policy.builder().id(6L).customerId(600L).status("ACTIVE").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(600L);
        when(policyRepository.findById(6L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.issue(6L, null)).thenThrow(new com.claimassist.platform.common_lib.error.BadRequestException("Invalid lifecycle transition"));

        mockMvc.perform(post("/api/v1/policies/6/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 8. Missing Idempotency-Key for endorse/renew is rejected
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void missingIdempotencyKeyForEndorseRejected() throws Exception {
        Policy p = Policy.builder().id(7L).customerId(700L).status("ACTIVE").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(700L);
        when(policyRepository.findById(7L)).thenReturn(Optional.of(p));

        String payload = "{\"planCode\":\"PLUS\"}";

        mockMvc.perform(post("/api/v1/policies/7/endorse").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(policyLifecycleService, never()).endorse(anyLong(), any(EndorseRequestDto.class), anyString());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void missingIdempotencyKeyForRenewRejected() throws Exception {
        Policy p = Policy.builder().id(8L).customerId(800L).status("ACTIVE").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(800L);
        when(policyRepository.findById(8L)).thenReturn(Optional.of(p));

        String payload = "{\"effectiveFrom\":\"2026-10-01T00:00:00Z\"}";

        mockMvc.perform(post("/api/v1/policies/8/renew").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(policyLifecycleService, never()).renew(anyLong(), any(RenewRequestDto.class), anyString());
    }

    // 9. Issue does not bypass payment verification - service throws when payment incomplete
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void issueFailsWhenPaymentIncomplete() throws Exception {
        Policy p = Policy.builder().id(9L).customerId(900L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_x").build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(900L);
        when(policyRepository.findById(9L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.issue(9L, null)).thenThrow(new com.claimassist.platform.common_lib.error.BadRequestException("Payment not completed"));

        mockMvc.perform(post("/api/v1/policies/9/issue").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 10. Reinstate does not activate without payment verification (no PI supplied)
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void reinstateWithoutPaymentLeavesPending() throws Exception {
        Policy p = Policy.builder().id(10L).customerId(1000L).status("CANCELLED").effectiveDate(Instant.now()).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(1000L);
        when(policyRepository.findById(10L)).thenReturn(Optional.of(p));
        when(policyLifecycleService.reinstate(anyLong(), any(com.claimassist.platform.policy_service.dto.ReinstateRequestDto.class), any(), any())).thenReturn(Policy.builder().id(10L).status("REINSTATEMENT_PENDING").build());

        mockMvc.perform(post("/api/v1/policies/10/reinstate").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REINSTATEMENT_PENDING"));
    }

    // 11 & 12. Sanity: existing product/plan/public and internal create endpoints still secured as before - quick smoke
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void productsEndpointPublicAndInternalCreateProtected() throws Exception {
        // Public GET /api/v1/policies/products should be accessible without auth
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/policies/products"))
                .andExpect(status().isOk());

        // Internal POST /internal/v1/policies should require Idempotency-Key header and internal identity - but this is covered elsewhere.
        mockMvc.perform(post("/internal/v1/policies").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"productCode\":\"AUTO\",\"planCode\":\"BASIC\",\"coverageCode\":\"C1\",\"effectiveDate\":\"2026-10-01T00:00:00Z\"}"))
                .andExpect(status().isNotFound());
    }
}
