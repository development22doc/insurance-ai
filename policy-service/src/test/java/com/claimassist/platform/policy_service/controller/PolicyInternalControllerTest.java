package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.exception.GlobalExceptionHandler;
import com.claimassist.platform.policy_service.service.PolicyCoverageService;
import com.claimassist.platform.policy_service.security.InternalRequestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyInternalControllerTest {

    @Mock
    private PolicyCoverageService policyCoverageService;

    @Mock
    private InternalRequestIdentity internalRequestIdentity;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PolicyInternalController(policyCoverageService, internalRequestIdentity))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void getPolicyCoverage_invalidPolicyId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/internal/v1/policies/-1/coverage"))
                .andExpect(status().isBadRequest());
    }
}
