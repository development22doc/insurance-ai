package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ApiError;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.SharedExceptionAutoConfiguration;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.*;
import com.claimassist.platform.policy_service.security.TestExceptionAdvice;
import com.claimassist.platform.policy_service.security.TestJwtDecoderConfig;
import com.claimassist.platform.policy_service.service.PlanCatalogCommandService;
import com.claimassist.platform.policy_service.service.PolicyCreationService;
import com.claimassist.platform.policy_service.service.ProductCatalogCommandService;
import com.claimassist.platform.policy_service.service.PublicPolicyQueryService;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "security.internal.trusted-service-client-ids=claimassist-admin-service"
})
@AutoConfigureMockMvc
@Import({SharedExceptionAutoConfiguration.class, TestJwtDecoderConfig.class, TestExceptionAdvice.class, PolicyControllerCommandTest.TestControllerExceptionAdvice.class})
class PolicyControllerCommandTest {

    @RestControllerAdvice
    static class TestControllerExceptionAdvice {
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiError> handle(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError(HttpStatus.NOT_FOUND, ex.getMessage()));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicPolicyQueryService publicPolicyQueryService;

    @MockBean
    private ProductCatalogCommandService productCatalogCommandService;

    @MockBean
    private PlanCatalogCommandService planCatalogCommandService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private PolicyCreationService policyCreationService;

    @MockBean
    private com.claimassist.platform.policy_service.repository.ProductRepository productRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PlanRepository planRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PolicyRepository policyRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.CoverageRepository coverageRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.ProcessedStripeEventRepository processedStripeEventRepository;

    @MockBean
    private com.claimassist.platform.policy_service.repository.PolicyVersionRepository policyVersionRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_success() throws Exception {
        ProductDto dto = new ProductDto(1L, "AUTO", "Auto Cover", true, Instant.now());
        when(productCatalogCommandService.createProduct(any(ProductCreateRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/policies/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUTO\",\"name\":\"Auto Cover\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTO"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_validationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/policies/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProduct_success() throws Exception {
        ProductDto dto = new ProductDto(1L, "AUTO", "Updated Auto Cover", true, Instant.now());
        when(productCatalogCommandService.updateProduct(eq(1L), any(ProductUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/policies/products/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUTO\",\"name\":\"Updated Auto Cover\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Auto Cover"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProduct_missingProduct() throws Exception {
        when(productCatalogCommandService.updateProduct(eq(99L), any(ProductUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", "99"));

        mockMvc.perform(put("/api/v1/policies/products/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUTO\",\"name\":\"Auto Cover\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProductStatus_success() throws Exception {
        ProductDto dto = new ProductDto(1L, "AUTO", "Auto Cover", false, Instant.now());
        when(productCatalogCommandService.updateProductStatus(eq(1L), any(ProductStatusUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/policies/products/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createProduct_unauthorizedUser() throws Exception {
        mockMvc.perform(post("/api/v1/policies/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUTO\",\"name\":\"Auto Cover\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlan_success() throws Exception {
        PlanDto dto = new PlanDto(10L, "BASIC", "Basic", true, 1L, "AUTO", 2000L, 50000L, Instant.now());
        when(planCatalogCommandService.createPlan(eq(1L), any(PlanCreateRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/policies/products/1/plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Basic\",\"deductibleCents\":2000,\"coverageLimitCents\":50000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BASIC"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlan_missingParentProduct() throws Exception {
        when(planCatalogCommandService.createPlan(eq(99L), any(PlanCreateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", "99"));

        mockMvc.perform(post("/api/v1/policies/products/99/plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Basic\",\"deductibleCents\":2000,\"coverageLimitCents\":50000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPlan_validationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/policies/products/1/plans")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\",\"deductibleCents\":-1,\"coverageLimitCents\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePlan_success() throws Exception {
        PlanDto dto = new PlanDto(10L, "BASIC", "Improved Basic", true, 1L, "AUTO", 2500L, 55000L, Instant.now());
        when(planCatalogCommandService.updatePlan(eq(10L), any(PlanUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/policies/plans/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Improved Basic\",\"deductibleCents\":2500,\"coverageLimitCents\":55000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Improved Basic"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePlan_missingPlan() throws Exception {
        when(planCatalogCommandService.updatePlan(eq(50L), any(PlanUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Plan", "50"));

        mockMvc.perform(put("/api/v1/policies/plans/50")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BASIC\",\"name\":\"Basic\",\"deductibleCents\":2000,\"coverageLimitCents\":50000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePlanStatus_success() throws Exception {
        PlanDto dto = new PlanDto(10L, "BASIC", "Basic", false, 1L, "AUTO", 2000L, 50000L, Instant.now());
        when(planCatalogCommandService.updatePlanStatus(eq(10L), any(PlanStatusUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/policies/plans/10/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updatePlan_unauthorizedUser() throws Exception {
        mockMvc.perform(patch("/api/v1/policies/plans/10/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }
}
