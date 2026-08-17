package com.claimassist.platform.claims_service.service.command.impl;

import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.entity.ClaimPartyId;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.mapper.ClaimMapper;
import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommands;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands;
import com.claimassist.platform.claims_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.claims_service.support.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimCommandServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimPartyRepository claimPartyRepository;

    @Mock
    private ClaimStatusHistoryRepository claimStatusHistoryRepository;

    @Mock
    private ClaimMapper claimMapper;

    @Mock
    private CustomerServiceGateway customerServiceGateway;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private PerformanceLogger performanceLogger;

    @InjectMocks
    private ClaimCommandServiceImpl claimCommandService;

    private ClaimCommands.SubmitClaimCommand submitCommand;
    private ClaimCommands.UpdateClaimStatusCommand statusCommand;

    @BeforeEach
    void setUp() {
submitCommand = new ClaimCommands.SubmitClaimCommand(
                1L, "POL-001", Instant.now(),
                50000L, 1L, "test-key-123");

        statusCommand = new ClaimCommands.UpdateClaimStatusCommand(
                1L, "UNDER_REVIEW", null, "admin-user");
    }

    @Test
    void submitClaim_WithActivePolicy_ShouldSubmitClaimSuccess() {
        // Given
        when(customerServiceGateway.getPolicyCoverage(1L, 1L)).thenReturn(new com.claimassist.platform.common_lib.dto.PolicyCoverageDto(
                1L, "POL-001", "ACTIVE", "Plan Type A", "Health", 1000L, 100000L, null));

        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            claim.setId(1L);
            return claim;
        });

        when(idempotencyService.execute(
                eq("test-key-123"),
                eq("claims.submitClaim"),
                eq(1L),
                eq(ClaimResponse.class),
                any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(4)).get());

        when(claimMapper.toClaimResponse(any(Claim.class))).thenReturn(new ClaimResponse(1L, "CLM-1", "SUBMITTED", "ACCIDENT"));

        // When
        ClaimResponse response = claimCommandService.submitClaim(submitCommand);

        // Then
        assertThat(response).isNotNull();
        verify(claimRepository).save(any(Claim.class));
        verify(idempotencyService).execute(
                eq("test-key-123"),
                eq("claims.submitClaim"),
                eq(1L),
                eq(ClaimResponse.class),
                any());
    }

    @Test
    void submitClaim_WithInactivePolicy_ShouldThrowBadRequestException() {
        // Given
        when(customerServiceGateway.getPolicyCoverage(1L, 1L)).thenReturn(new com.claimassist.platform.common_lib.dto.PolicyCoverageDto(
                1L, "POL-001", "EXPIRED", "Plan Type A", "Health", 1000L, 100000L, null));

        when(idempotencyService.execute(
                eq("test-key-123"),
                eq("claims.submitClaim"),
                eq(1L),
                eq(ClaimResponse.class),
                any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(4)).get());

        // When & Then
        assertThatThrownBy(() -> claimCommandService.submitClaim(submitCommand))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot file a claim against a policy that is not ACTIVE");

        verify(claimRepository, never()).save(any());
    }

    @Test
    void applyStatusChange_WithValidTransition_ShouldApplyStatusChange() {
        // Given: SUBMITTED -> UNDER_REVIEW is the only legal transition out of SUBMITTED.
        Claim existingClaim = Claim.builder()
                .id(1L)
                .policyId(1L)
                .status(ClaimStatus.SUBMITTED)
                .build();

        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(existingClaim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Claim result = claimCommandService.applyStatusChange(statusCommand);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        verify(claimRepository).findById(1L);
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    void applyStatusChange_WithInvalidFromStatus_ShouldThrowClaimStateTransitionException() {
        // Given: DENIED may only transition to CLOSED, so a move to UNDER_REVIEW is illegal.
        Claim existingClaim = Claim.builder()
                .id(1L)
                .status(ClaimStatus.DENIED)
                .build();

        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(existingClaim));

        // When & Then
        assertThatThrownBy(() -> claimCommandService.applyStatusChange(statusCommand))
                .isInstanceOf(ClaimStateTransitionException.class)
                .hasMessageContaining("DENIED", "UNDER_REVIEW");

        verify(claimRepository, never()).save(any());
    }

    @Test
    void applyStatusChange_WithUnknownStatus_ShouldThrowBadRequestException() {
        // Given
        statusCommand = new ClaimCommands.UpdateClaimStatusCommand(
                1L, "UNKNOWN_STATUS", null, "admin-user");

        Claim existingClaim = Claim.builder()
                .id(1L)
                .status(ClaimStatus.SUBMITTED)
                .build();

        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(existingClaim));

        // When & Then
        assertThatThrownBy(() -> claimCommandService.applyStatusChange(statusCommand))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown claim status");
    }

    @Test
    void applyStatusChange_WithNonExistentClaim_ShouldThrowResourceNotFoundException() {
        // Given
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        // When & Then
        assertThatThrownBy(() -> claimCommandService.applyStatusChange(statusCommand))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Claim", String.valueOf(1L));

        verify(claimRepository, never()).save(any());
    }

    }
