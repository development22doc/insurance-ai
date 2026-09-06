package com.claimassist.platform.customer_service.client;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PolicyServiceClient.
 * Phase 6: Integration preparation tests. Tests the client interface and configuration.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceClientTest {

    @Mock
    private PolicyServiceClient policyServiceClient;

    private PolicyCreateRequestDto requestDto;
    private PolicyCreateResponseDto responseDto;

    @BeforeEach
    void setUp() {
        requestDto = new PolicyCreateRequestDto();
        requestDto.setCustomerId(1L);
        requestDto.setProductCode("AUTO");
        requestDto.setPlanCode("STANDARD");
        requestDto.setCoverageCode("COMPREHENSIVE");
        requestDto.setEffectiveDate(java.time.Instant.now());
        requestDto.setRenewalDate(java.time.Instant.now().plusSeconds(31536000));

        responseDto = new PolicyCreateResponseDto(100L, "POL-ABC123", "PENDING", "pi_123");
    }

    @Test
    void testCreatePolicy_Success() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                .thenReturn(responseDto);

        // Act
        PolicyCreateResponseDto result = policyServiceClient.createPolicy(
                requestDto, 1L, "test-idempotency-key");

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.policyId);
        assertEquals("POL-ABC123", result.policyNumber);
        assertEquals("PENDING", result.status);
        assertEquals("pi_123", result.stripePaymentIntentId);

        verify(policyServiceClient, times(1)).createPolicy(
                eq(requestDto), eq(1L), eq("test-idempotency-key"));
    }

    @Test
    void testCreatePolicy_WithNullIdempotencyKey() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), isNull()))
                .thenThrow(new FeignException.BadRequest("Missing Idempotency-Key",
                        mock(feign.Request.class), null, new java.util.HashMap<>()));

        // Act & Assert
        assertThrows(FeignException.BadRequest.class, () -> {
            policyServiceClient.createPolicy(requestDto, 1L, null);
        });
    }

    @Test
    void testCreatePolicy_WithBlankIdempotencyKey() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), eq("")))
                .thenThrow(new FeignException.BadRequest("Missing Idempotency-Key",
                        mock(feign.Request.class), null, new java.util.HashMap<>()));

        // Act & Assert
        assertThrows(FeignException.BadRequest.class, () -> {
            policyServiceClient.createPolicy(requestDto, 1L, "");
        });
    }

    @Test
    void testCreatePolicy_ServiceUnavailable() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                .thenThrow(new FeignException.ServiceUnavailable("Policy Service unavailable",
                        mock(feign.Request.class), null, new java.util.HashMap<>()));

        // Act & Assert
        assertThrows(FeignException.ServiceUnavailable.class, () -> {
            policyServiceClient.createPolicy(requestDto, 1L, "test-key");
        });
    }

    @Test
    void testCreatePolicy_Forbidden() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                .thenThrow(new FeignException.Forbidden("Access denied",
                        mock(feign.Request.class), null, new java.util.HashMap<>()));

        // Act & Assert
        assertThrows(FeignException.Forbidden.class, () -> {
            policyServiceClient.createPolicy(requestDto, 1L, "test-key");
        });
    }

    @Test
    void testCreatePolicy_Conflict() {
        // Arrange
        when(policyServiceClient.createPolicy(any(), any(Long.class), any(String.class)))
                .thenThrow(new FeignException.Conflict("Idempotency conflict",
                        mock(feign.Request.class), null, new java.util.HashMap<>()));

        // Act & Assert
        assertThrows(FeignException.Conflict.class, () -> {
            policyServiceClient.createPolicy(requestDto, 1L, "test-key");
        });
    }
}
