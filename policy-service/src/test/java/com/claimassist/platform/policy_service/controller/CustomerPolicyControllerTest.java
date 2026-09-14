package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.CustomerPolicyDetailDto;
import com.claimassist.platform.policy_service.dto.CustomerPolicySummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyPeriodDto;
import com.claimassist.platform.policy_service.exception.GlobalExceptionHandler;
import com.claimassist.platform.policy_service.service.CustomerPolicyReadService;
import com.claimassist.platform.policy_service.service.PolicyContractReadService;
import com.claimassist.platform.policy_service.service.PolicyLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerPolicyControllerTest {

    @Mock
    private CustomerPolicyReadService customerPolicyReadService;

    @Mock
    private PolicyContractReadService policyContractReadService;

    @Mock
    private PolicyLifecycleService policyLifecycleService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerPolicyController(customerPolicyReadService, policyContractReadService, policyLifecycleService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void getMyPolicies_returnsListForCurrentCustomer() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(customerPolicyReadService.getPoliciesForCustomer(42L)).thenReturn(List.of(
                new CustomerPolicySummaryDto(
                        1L,
                        "POL-1",
                        "ACTIVE",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        10L,
                        "Auto Insurance",
                        "AUTO",
                        20L,
                        "Basic Auto",
                        25000L,
                        4000L,
                        500000L,
                        "INR"
                )
        ));

        mockMvc.perform(get("/api/v1/customers/me/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyNumber").value("POL-1"));
    }

    @Test
    void getMyPolicy_returnsCustomerPolicyDetail() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(customerPolicyReadService.getPolicyForCustomer(42L, 7L)).thenReturn(new CustomerPolicyDetailDto(
                7L,
                "POL-7",
                "ACTIVE",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                10L,
                "Auto Insurance",
                "AUTO",
                20L,
                "Basic Auto",
                25000L,
                4000L,
                500000L,
                "INR"
        ));

        mockMvc.perform(get("/api/v1/customers/me/policies/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Basic Auto"));
    }

    @Test
    void getMyPolicy_notFound_returns404() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(customerPolicyReadService.getPolicyForCustomer(42L, 99L))
                .thenThrow(new ResourceNotFoundException("Policy", "99"));

        mockMvc.perform(get("/api/v1/customers/me/policies/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyPolicyPeriods_returnsHistoryForCurrentCustomer() throws Exception {
        when(policyContractReadService.getPeriodsForPolicy(7L)).thenReturn(List.of(
                new PolicyPeriodDto(
                        11L,
                        7L,
                        null,
                        0,
                        "ACTIVE",
                        20L,
                        "Basic Auto",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z"),
                        null,
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/v1/customers/me/policies/7/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyPeriodId").value(11));
    }

    @Test
    void getCurrentPolicyPeriod_returnsContractCurrentPeriod() throws Exception {
        when(policyContractReadService.getCurrentPeriod(7L)).thenReturn(
                new PolicyPeriodDto(
                        11L,
                        7L,
                        null,
                        0,
                        "ACTIVE",
                        20L,
                        "Basic Auto",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z"),
                        null,
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/customers/me/policies/7/current-period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Basic Auto"));
    }

    @Test
    void cancelPolicy_cancelsPolicyAndReturnsUpdatedDetail() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(customerPolicyReadService.getPolicyForCustomer(42L, 7L)).thenReturn(new CustomerPolicyDetailDto(
                7L,
                "POL-7",
                "CANCELLED",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                10L,
                "Auto Insurance",
                "AUTO",
                20L,
                "Basic Auto",
                25000L,
                4000L,
                500000L,
                "INR"
        ));

        mockMvc.perform(post("/api/v1/customers/me/policies/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(policyLifecycleService).cancelPolicy(7L, 42L, null);
    }
}
