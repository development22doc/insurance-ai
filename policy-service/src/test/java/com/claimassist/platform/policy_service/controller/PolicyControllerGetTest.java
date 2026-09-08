package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ApiError;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.security.TestJwtDecoderConfig;
import com.claimassist.platform.policy_service.security.TestExceptionAdvice;
import com.claimassist.platform.policy_service.service.PolicyLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(controllers = PolicyController.class)
@Import({SharedExceptionAutoConfiguration.class, TestJwtDecoderConfig.class, TestExceptionAdvice.class, PolicyControllerGetTest.TestControllerAdvice.class})
class PolicyControllerGetTest {

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class TestControllerAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiError> handle(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(HttpStatus.NOT_FOUND, ex.getMessage()));
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private PolicyLookupService policyLookupService;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PolicyRepository policyRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    // Additional dependencies of PolicyController mocked for WebMvc slice
    @MockBean
    private com.claimassist.platform.policy_service.service.PublicPolicyQueryService publicPolicyQueryService;

    @MockBean
    private com.claimassist.platform.policy_service.service.ProductCatalogCommandService productCatalogCommandService;

    @MockBean
    private com.claimassist.platform.policy_service.service.PlanCatalogCommandService planCatalogCommandService;

    @MockBean
    private com.claimassist.platform.policy_service.service.PolicyLifecycleService policyLifecycleService;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void ownerCanGetOwnPolicy() throws Exception {
        long policyId = 1L;
        long customerId = 7L;
        PolicySummaryDto dto = new PolicySummaryDto(policyId, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        com.claimassist.platform.policy_service.entity.Policy p = com.claimassist.platform.policy_service.entity.Policy.builder()
                .id(policyId)
                .customerId(customerId)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(customerId);
        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.of(p));
        when(policyLookupService.getPolicy(eq(policyId), eq(customerId))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value("POL-1"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonOwnerGetsForbidden() throws Exception {
        long policyId = 1L;
        long policyOwner = 7L;
        long caller = 8L;

        com.claimassist.platform.policy_service.entity.Policy p = com.claimassist.platform.policy_service.entity.Policy.builder()
                .id(policyId)
                .customerId(policyOwner)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(caller);
        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGetAnotherCustomerPolicy() throws Exception {
        long policyId = 1L;
        long policyOwner = 7L;

        PolicySummaryDto dto = new PolicySummaryDto(policyId, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        com.claimassist.platform.policy_service.entity.Policy p = com.claimassist.platform.policy_service.entity.Policy.builder()
                .id(policyId)
                .customerId(policyOwner)
                .build();

        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.of(p));
        when(policyLookupService.getPolicyForAdmin(eq(policyId))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value("POL-1"));
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsCanGetAnotherCustomerPolicy() throws Exception {
        long policyId = 1L;
        long policyOwner = 7L;

        PolicySummaryDto dto = new PolicySummaryDto(policyId, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        com.claimassist.platform.policy_service.entity.Policy p = com.claimassist.platform.policy_service.entity.Policy.builder()
                .id(policyId)
                .customerId(policyOwner)
                .build();

        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.of(p));
        when(policyLookupService.getPolicyForAdmin(eq(policyId))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value("POL-1"));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void missingPolicyReturns404() throws Exception {
        long policyId = 99L;
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/policies/99").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void returnedDataMatchesProjection() throws Exception {
        long policyId = 1L;
        long customerId = 7L;
        PolicySummaryDto dto = new PolicySummaryDto(policyId, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        com.claimassist.platform.policy_service.entity.Policy p = com.claimassist.platform.policy_service.entity.Policy.builder()
                .id(policyId)
                .customerId(customerId)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(customerId);
        when(policyRepository.findById(eq(policyId))).thenReturn(Optional.of(p));
        when(policyLookupService.getPolicy(eq(policyId), eq(customerId))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value("POL-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.productType").value("AUTO"));
    }
}
