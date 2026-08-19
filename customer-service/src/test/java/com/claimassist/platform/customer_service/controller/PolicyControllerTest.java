package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import com.claimassist.platform.customer_service.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    @Mock private PolicyService policyService;
    @Mock private PolicyQueryService policyQueryService;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks private PolicyController controller;

    private PolicyResponse response;

    @BeforeEach
    void setUp() {
        response = new PolicyResponse(10L, "POL-1", "ACTIVE", "Comprehensive", "AUTO",
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void createPolicy_returnsOk() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        PolicyCreateRequest request = new PolicyCreateRequest(2L, Instant.parse("2026-01-01T00:00:00Z"), null);
        when(policyService.createPolicy(request, 1L)).thenReturn(response);

        ResponseEntity<PolicyResponse> result = controller.createPolicy(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void getMyPolicies_returnsList() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(policyQueryService.getMyPolicies(1L)).thenReturn(List.of(response));

        ResponseEntity<List<PolicyResponse>> result = controller.getMyPolicies();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getPolicyById_returnsPolicy() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(policyService.getPolicyById(10L, 1L)).thenReturn(response);

        ResponseEntity<PolicyResponse> result = controller.getPolicyById(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().id()).isEqualTo(10L);
    }

    @Test
    void updatePolicy_returnsUpdated() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        PolicyUpdateRequest request = new PolicyUpdateRequest("CANCELLED", null);
        when(policyService.updatePolicy(10L, request, 1L)).thenReturn(response);

        ResponseEntity<PolicyResponse> result = controller.updatePolicy(10L, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void deletePolicy_returnsNoContent() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        ResponseEntity<Void> result = controller.deletePolicy(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(policyService).deletePolicy(10L, 1L);
    }
}