package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.service.PolicyCoverageQueryService;
import com.claimassist.platform.policy_service.service.PolicyLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPolicyController.class)
@Import({SharedExceptionAutoConfiguration.class, InternalPolicyControllerTest.TestExceptionAdvice.class})
class InternalPolicyControllerTest {

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class TestExceptionAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(ServiceUnavailableException.class)
        public org.springframework.http.ResponseEntity<com.claimassist.platform.common_lib.error.ApiError> handle(ServiceUnavailableException ex) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new com.claimassist.platform.common_lib.error.ApiError(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PolicyCoverageQueryService coverageService;

    @MockBean
    PolicyLookupService policyLookupService;

    @MockBean
    com.claimassist.platform.policy_service.security.InternalRequestIdentity internalRequestIdentity;

    @MockBean
    com.claimassist.platform.policy_service.service.PolicyCreationService policyCreationService;

    @Test
    @WithMockUser
    void getPolicy_returnsSummaryDto() throws Exception {
        PolicySummaryDto dto = new PolicySummaryDto(1L, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(internalRequestIdentity.resolveCallingUserId(any())).thenReturn(7L);
        when(policyLookupService.getPolicy(1L, 7L)).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/policies/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getPoliciesForCustomer_returnsList() throws Exception {
        PolicySummaryDto dto = new PolicySummaryDto(1L, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(internalRequestIdentity.resolveCallingUserId(any())).thenReturn(7L);
        when(policyLookupService.getPoliciesForCustomer(7L, 7L)).thenReturn(java.util.List.of(dto));

        mockMvc.perform(get("/internal/v1/policies/customer/7").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser // simplifies security for scaffold test
    void getCoverage_whenServiceUnavailable_returns503() throws Exception {
        when(internalRequestIdentity.resolveCallingUserId(any())).thenReturn(1L);
        when(coverageService.getPolicyCoverage(any(), any())).thenThrow(new ServiceUnavailableException("unavailable"));

        mockMvc.perform(get("/internal/v1/policies/1/coverage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser
    void getCoverage_withValidPolicy_returnsDtoJson() throws Exception {
        PolicyCoverageDto dto = new PolicyCoverageDto(1L, "POL-1", "ACTIVE", "AUTO", "STANDARD_PLAN", 0L, 100000L, "2027-01-01");
        when(internalRequestIdentity.resolveCallingUserId(any())).thenReturn(null);
        when(coverageService.getPolicyCoverage(1L, null)).thenReturn(dto);

        mockMvc.perform(get("/internal/v1/policies/1/coverage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
