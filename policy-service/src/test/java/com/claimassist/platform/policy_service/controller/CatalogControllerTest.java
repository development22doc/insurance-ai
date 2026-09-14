package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.exception.GlobalExceptionHandler;
import com.claimassist.platform.policy_service.dto.PlanDetailDto;
import com.claimassist.platform.policy_service.dto.ProductDetailDto;
import com.claimassist.platform.policy_service.dto.ProductSummaryDto;
import com.claimassist.platform.policy_service.service.ProductCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private ProductCatalogService productCatalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(productCatalogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void getProducts_returnsCatalog() throws Exception {
        when(productCatalogService.getProducts()).thenReturn(List.of(
                new ProductSummaryDto(1L, "AUTO", "Auto Insurance", "AUTO", "Vehicle cover", "ACTIVE")
        ));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("AUTO"));
    }

    @Test
    void getProduct_returnsDetail() throws Exception {
        when(productCatalogService.getProduct(1L)).thenReturn(new ProductDetailDto(
                1L,
                "AUTO",
                "Auto Insurance",
                "AUTO",
                "Vehicle cover",
                "ACTIVE",
                List.of()
        ));

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Auto Insurance"));
    }

    @Test
    void getPlan_returnsDetail() throws Exception {
        when(productCatalogService.getPlan(7L)).thenReturn(new PlanDetailDto(
                7L,
                1L,
                "AUTO",
                "Auto Insurance",
                "AUTO_BASIC",
                "Basic Auto",
                "ACTIVE",
                50000L,
                10000L,
                1000000L,
                "INR"
        ));

        mockMvc.perform(get("/api/v1/plans/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("AUTO"));
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productCatalogService.getProduct(99L))
                .thenThrow(new ResourceNotFoundException("Product", "99"));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound());
    }
}
