package com.claimassist.platform.customer_service.client;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import feign.FeignException;
import feign.Request;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PolicyServiceAdapter.
 * Phase 6: Integration preparation tests. Tests request/response mapping and error handling.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceAdapterTest {

    @Mock
    private PolicyServiceClient policyServiceClient;

    @Mock
    private CoveragePlanRepository coveragePlanRepository;

    @Mock
    private com.claimassist.platform.customer_service.config.PolicyServiceProperties policyServiceProperties;

    private PolicyServiceAdapter adapter;
    private CoveragePlan coveragePlan;
    private PolicyCreateRequest customerRequest;
    private PolicyCreateResponseDto policyServiceResponse;

    @BeforeEach
    void setUp() {
        // Default to no mapping configured to preserve existing blocking behavior unless a test overrides it
        org.mockito.Mockito.lenient().when(policyServiceProperties.getMappingForCoveragePlan(anyLong())).thenReturn(java.util.Optional.empty());

        adapter = new PolicyServiceAdapter(policyServiceClient, coveragePlanRepository, policyServiceProperties);

        coveragePlan = CoveragePlan.builder()
                .id(1L)
                .name("Standard")
                .productType("AUTO")
                .annualPremiumCents(50000L)
                .deductibleCents(1000L)
                .coverageLimitCents(100000L)
                .build();

        customerRequest = new PolicyCreateRequest(
                1L,
                Instant.now(),
                Instant.now().plusSeconds(31536000)
        );

        policyServiceResponse = new PolicyCreateResponseDto(
                100L,
                "POL-ABC123",
                "PENDING",
                "pi_123"
        );
    }

    @Test
    void testCreatePolicyViaPolicyService_Success() {
        // Arrange
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(coveragePlan));

        // Act & Assert - should fail because coverage mapping cannot be established
        assertThrows(BadRequestException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, "test-idempotency-key");
        });

        // Verify Policy Service was never called (mapping blocked the request)
        verify(policyServiceClient, never()).createPolicy(any(), any(), any());
    }

    @Test
    void testCreatePolicyViaPolicyService_WithConfigMapping_CallsPolicyService() {
        // Arrange
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(coveragePlan));
        com.claimassist.platform.customer_service.config.PolicyServiceProperties.Mapping m = new com.claimassist.platform.customer_service.config.PolicyServiceProperties.Mapping();
        m.setProductCode("AUTO");
        m.setPlanCode("STANDARD");
        m.setCoverageCode("COMPREHENSIVE");
        when(policyServiceProperties.getMappingForCoveragePlan(1L)).thenReturn(java.util.Optional.of(m));

        when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                .thenReturn(policyServiceResponse);

        // Act
        com.claimassist.platform.customer_service.dto.policy.PolicyResponse resp = adapter.createPolicyViaPolicyService(customerRequest, 1L, "test-key");

        // Assert - adapter returned a response mapped from Policy Service
        assertNotNull(resp);

        // Verify Policy Service was invoked once
        verify(policyServiceClient, times(1)).createPolicy(any(), any(), any());
    }

    @Test
    void testCreatePolicyViaPolicyService_IdempotencyKeyPropagatedUnchanged() {
        // Arrange
        String originalKey = "original-idempotency-key-12345";
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(coveragePlan));

        // Act & Assert - should fail because coverage mapping cannot be established
        assertThrows(BadRequestException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, originalKey);
        });

        // Verify Policy Service was never called (mapping blocked the request)
        verify(policyServiceClient, never()).createPolicy(any(), any(), any());
    }

    @Test
    void testCreatePolicyViaPolicyService_CoverageMappingBlocked() {
        // Arrange
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(coveragePlan));

        // Act & Assert - should fail because coverage mapping cannot be established
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, "test-key");
        });

        // Assert the error message explicitly mentions the mapping limitation
        assertTrue(exception.getMessage().contains("CoveragePlan → Product/Plan/Coverage mapping cannot be safely established"));
        assertTrue(exception.getMessage().contains("explicitly unavailable"));
    }

    @Test
    void testCreatePolicyViaPolicyService_CoveragePlanNotFound() {
        // Arrange
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, "test-key");
        });

        verify(coveragePlanRepository, times(1)).findById(1L);
        verify(policyServiceClient, never()).createPolicy(any(), any(), any());
    }

    @Test
    void testCreatePolicyViaPolicyService_PolicyService403() {
        // Arrange
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(coveragePlan));

        // Mock static mapper to bypass the mapping block so the adapter calls the client
        try (var mocked = org.mockito.Mockito.mockStatic(PolicyServiceMapper.class)) {
            PolicyCreateRequestDto stubRequest = new PolicyCreateRequestDto();
            mocked.when(() -> PolicyServiceMapper.toPolicyServiceRequest(
                    coveragePlan,
                    1L,
                    customerRequest.effectiveDate(),
                    customerRequest.renewalDate(),
                    null,
                    null
            )).thenReturn(stubRequest);

            when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                    .thenThrow(new FeignException.Forbidden("Access denied",
                            mock(Request.class), null, new java.util.HashMap<>()));

            // Act & Assert - adapter should translate 403 -> AccessDeniedException (not ServiceUnavailable)
            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
                adapter.createPolicyViaPolicyService(customerRequest, 1L, "test-key");
            });

            assertTrue(ex.getMessage().contains("Policy Service access denied"));

            // Verify Policy Service was invoked once
            verify(policyServiceClient, times(1)).createPolicy(any(), any(), any());
        }
    }

    @Test
    void testCreatePolicyViaPolicyService_MissingIdempotencyKey() {
        // Act & Assert - should fail because idempotency key is required
        assertThrows(BadRequestException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, null);
        });

        // Verify Policy Service was never called (no fallback UUID generation)
        verify(policyServiceClient, never()).createPolicy(any(), any(), any());
    }

    @Test
    void testCreatePolicyViaPolicyService_BlankIdempotencyKey() {
        // Act & Assert - should fail because idempotency key is required
        assertThrows(BadRequestException.class, () -> {
            adapter.createPolicyViaPolicyService(customerRequest, 1L, "");
        });

        // Verify Policy Service was never called (no fallback UUID generation)
        verify(policyServiceClient, never()).createPolicy(any(), any(), any());
    }
}
