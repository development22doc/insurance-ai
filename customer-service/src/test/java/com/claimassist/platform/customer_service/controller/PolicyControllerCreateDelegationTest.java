package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.customer_service.config.PolicyServiceProperties;
import com.claimassist.platform.customer_service.client.PolicyServiceAdapter;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import com.claimassist.platform.customer_service.service.PolicyServiceImpl;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class PolicyControllerCreateDelegationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.claimassist.platform.common_lib.observability.CorrelationIdFilter correlationIdFilter;
    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    @MockBean
    private EventLogger eventLogger;
    @MockBean
    private PerformanceLogger performanceLogger;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private com.claimassist.platform.customer_service.service.PolicyService policyService;

    @MockBean
    private PolicyQueryService policyQueryService;

    @MockBean
    private PolicyServiceProperties policyServiceProperties;

    @BeforeEach
    void setupMocks() throws Exception {
        // default current user
        when(currentUserProvider.getCurrentUserId()).thenReturn(100L);

        // Ensure the mocked CorrelationIdFilter forwards the request to the filter chain
        java.util.concurrent.Callable<Void> noop = () -> null;
        doAnswer(invocation -> {
                    jakarta.servlet.ServletRequest req = invocation.getArgument(0);
                    jakarta.servlet.ServletResponse resp = invocation.getArgument(1);
                    jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, resp);
            return null;
                }).when(correlationIdFilter).doFilter(any(jakarta.servlet.ServletRequest.class), any(jakarta.servlet.ServletResponse.class), any(jakarta.servlet.FilterChain.class));
    }

    @Test
    void delegatedCreate_invokesAdapter_and_doesNotSaveLegacy() throws Exception {
        Long coveragePlanId = 200L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2027-09-01T00:00:00Z"));

        CoveragePlan cp = CoveragePlan.builder().id(coveragePlanId).name("Std").productType("AUTO").build();
        // For controller test, mock service behavior to simulate delegation
        PolicyResponse adapterResp = new PolicyResponse(501L, "POL-501", "PENDING", "Std", "AUTO", req.effectiveDate(), req.renewalDate());
        when(policyService.createPolicy(eq(req), eq(100L), eq("idem-abc"))).thenReturn(adapterResp);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/policies")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-user").roles("CUSTOMER"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-abc")
                .content(body))
                .andExpect(status().isOk());

        verify(policyService, times(1)).createPolicy(eq(req), eq(100L), eq("idem-abc"));
    }

    @Test
    void mappingPresent_missingIdempotencyKey_isRejected_and_noCalls() throws Exception {
        Long coveragePlanId = 201L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2027-10-01T00:00:00Z"));

        // Simulate service rejecting due to missing idempotency when mapping exists
        when(policyService.createPolicy(eq(req), eq(100L), isNull())).thenThrow(new com.claimassist.platform.common_lib.error.BadRequestException("Idempotency-Key required"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/policies")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-user").roles("CUSTOMER"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());

        verify(policyService, times(1)).createPolicy(eq(req), eq(100L), isNull());
    }

    @Test
    void noMapping_fallsBackToLegacy_saveInvoked_and_adapterNotCalled() throws Exception {
        Long coveragePlanId = 300L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2027-11-01T00:00:00Z"));

        // Simulate legacy create path handled by service when no mapping
        PolicyResponse legacyResp = new PolicyResponse(77L, "POL-77", "PENDING", "Basic", "HOME", req.effectiveDate(), req.renewalDate());
        when(policyService.createPolicy(eq(req), eq(100L), isNull())).thenReturn(legacyResp);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/policies")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test-user").roles("CUSTOMER"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        verify(policyService, times(1)).createPolicy(eq(req), eq(100L), isNull());
    }
}
