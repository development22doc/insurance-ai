package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import com.claimassist.platform.customer_service.service.PolicyService;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PolicyControllerUnitTest {

    @Mock
    private PolicyService policyService;
    @Mock
    private PolicyQueryService policyQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private PolicyController controller;

    @BeforeEach
    void setUp() {
        controller = new PolicyController(policyService, policyQueryService, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
    }

    @Test
    void controller_forwardsIdempotencyKey_whenPresent() {
        PolicyCreateRequest req = new PolicyCreateRequest(1L, Instant.now(), Instant.now().plusSeconds(1000));
        PolicyResponse resp = new PolicyResponse(9L, "POL-9", "PENDING", "Plan", "AUTO", req.effectiveDate(), req.renewalDate());
        when(policyService.createPolicy(eq(req), eq(42L), eq("idem-1"))).thenReturn(resp);

        var responseEntity = controller.createPolicy(req, "idem-1");
        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        verify(policyService, times(1)).createPolicy(eq(req), eq(42L), eq("idem-1"));
    }

    @Test
    void controller_allowsMissingIdempotencyKey_and_forwardsNull() {
        PolicyCreateRequest req = new PolicyCreateRequest(2L, Instant.now(), Instant.now().plusSeconds(1000));
        PolicyResponse resp = new PolicyResponse(10L, "POL-10", "PENDING", "Plan", "HOME", req.effectiveDate(), req.renewalDate());
        when(policyService.createPolicy(eq(req), eq(42L), isNull())).thenReturn(resp);

        var responseEntity = controller.createPolicy(req, null);
        assertNotNull(responseEntity);
        assertNotNull(responseEntity.getBody());
        verify(policyService, times(1)).createPolicy(eq(req), eq(42L), isNull());
    }
}
