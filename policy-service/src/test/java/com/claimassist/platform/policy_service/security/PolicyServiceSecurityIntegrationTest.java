package com.claimassist.platform.policy_service.security;

import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.policy_service.service.PolicyCoverageQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "security.internal.trusted-service-client-ids=claimassist-admin-service"
})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import({TestJwtDecoderConfig.class, com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration.class, TestExceptionAdvice.class})
class PolicyServiceSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PolicyCoverageQueryService policyCoverageQueryService;

    @MockBean
    com.claimassist.platform.policy_service.repository.PolicyRepository policyRepository;

    @MockBean
    com.claimassist.platform.policy_service.repository.PlanRepository planRepository;

    @MockBean
    com.claimassist.platform.policy_service.repository.ProductRepository productRepository;

    @MockBean
    com.claimassist.platform.policy_service.repository.CoverageRepository coverageRepository;

    @MockBean
    com.claimassist.platform.policy_service.repository.ProcessedStripeEventRepository processedStripeEventRepository;

    @MockBean
    com.claimassist.platform.policy_service.repository.PolicyVersionRepository policyVersionRepository;

    @MockBean
    com.claimassist.platform.policy_service.service.PolicyCreationService policyCreationService;

    @MockBean
    com.claimassist.platform.policy_service.service.PublicPolicyQueryService publicPolicyQueryService;

    @BeforeEach
    void setUp() {
        when(policyCoverageQueryService.getPolicyCoverage(any(), any()))
                .thenThrow(new ServiceUnavailableException("Policy service unavailable"));
    }

    private Jwt userJwt() {
        return Jwt.withTokenValue("user.token.1")
                .header("alg", "none")
                .claim("userId", 1L)
                .claim("preferred_username", "demo.user")
                .claim("azp", "claimassist-customer-app")
                .build();
    }

    private Jwt serviceJwt(String clientId) {
        return Jwt.withTokenValue("service.token." + clientId)
                .header("alg", "none")
                .claim("azp", clientId)
                .claim("client_id", clientId)
                .build();
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/internal/v1/policies/1/coverage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserToken_reachesController_and_returns503() throws Exception {
        mockMvc.perform(get("/internal/v1/policies/1/coverage").with(jwt().jwt(userJwt())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void authorizedServiceToken_reachesController_and_returns503() throws Exception {
        // Service callers must supply X-User-Id when using a service token. Provide header here.
        mockMvc.perform(get("/internal/v1/policies/1/coverage").with(jwt().jwt(serviceJwt("claimassist-admin-service"))).header("X-User-Id", "123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unauthorizedServiceToken_isRejected() throws Exception {
        // An authenticated but untrusted service must be rejected with 403 Forbidden
        mockMvc.perform(get("/internal/v1/policies/1/coverage").with(jwt().jwt(serviceJwt("evil-client"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void xUserIdHeader_withoutAuth_isRejected() throws Exception {
        // Header alone must not authenticate
        mockMvc.perform(get("/internal/v1/policies/1/coverage").header("X-User-Id", "123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void xUserId_with_unauthorized_service_isRejected() throws Exception {
        mockMvc.perform(get("/internal/v1/policies/1/coverage").with(jwt().jwt(serviceJwt("evil-client"))).header("X-User-Id", "123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedServiceToken_withXUserId_reachesController_and_returns503() throws Exception {
        mockMvc.perform(get("/internal/v1/policies/1/coverage").with(jwt().jwt(serviceJwt("claimassist-admin-service"))).header("X-User-Id", "123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }
}
