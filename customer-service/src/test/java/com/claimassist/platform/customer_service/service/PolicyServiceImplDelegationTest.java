package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.customer_service.client.PolicyServiceAdapter;
import com.claimassist.platform.customer_service.config.PolicyServiceProperties;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceImplDelegationTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CoveragePlanRepository coveragePlanRepository;
    @Mock
    private PolicyMapper policyMapper;
    @Mock
    private PolicyQueryService policyQueryService;
    @Mock
    private com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger;
    @Mock
    private com.claimassist.platform.common_lib.observability.PerformanceLogger performanceLogger;

    @Mock
    private PolicyServiceAdapter policyServiceAdapter;

    @Mock
    private PolicyServiceProperties policyServiceProperties;

    private PolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolicyServiceImpl(
                policyRepository,
                customerRepository,
                coveragePlanRepository,
                policyMapper,
                policyQueryService,
                eventLogger,
                performanceLogger,
                policyServiceAdapter,
                policyServiceProperties
        );
    }

    @Test
    void delegatedCreate_invokesAdapter_and_doesNotSaveLegacy() {
        // Arrange
        Long customerId = 10L;
        Long coveragePlanId = 1L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.now(), Instant.now().plusSeconds(31536000));

        CoveragePlan cp = CoveragePlan.builder().id(coveragePlanId).name("Standard").productType("AUTO").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(coveragePlanRepository.findById(coveragePlanId)).thenReturn(Optional.of(cp));
        // mapping present
        PolicyServiceProperties.Mapping m = new PolicyServiceProperties.Mapping();
        m.setProductCode("AUTO"); m.setPlanCode("STANDARD"); m.setCoverageCode("COV");
        when(policyServiceProperties.getMappingForCoveragePlan(coveragePlanId)).thenReturn(Optional.of(m));

        PolicyResponse stubResp = new PolicyResponse(123L, "POL-123", "PENDING", "Standard", "AUTO", req.effectiveDate(), req.renewalDate());
        when(policyServiceAdapter.createPolicyViaPolicyService(eq(req), eq(customerId), eq("idem-1"))).thenReturn(stubResp);

        // Act
        PolicyResponse resp = service.createPolicy(req, customerId, "idem-1");

        // Assert
        assertNotNull(resp);
        assertEquals(123L, resp.id());
        verify(policyServiceAdapter, times(1)).createPolicyViaPolicyService(eq(req), eq(customerId), eq("idem-1"));
        verify(policyRepository, never()).save(ArgumentMatchers.any());
    }

    @Test
    void mappingPresent_missingIdempotencyKey_isRejected_and_noCalls() {
        // Arrange
        Long customerId = 10L;
        Long coveragePlanId = 2L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.now(), Instant.now().plusSeconds(31536000));

        CoveragePlan cp = CoveragePlan.builder().id(coveragePlanId).name("Standard").productType("AUTO").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(coveragePlanRepository.findById(coveragePlanId)).thenReturn(Optional.of(cp));
        // mapping present
        PolicyServiceProperties.Mapping m = new PolicyServiceProperties.Mapping();
        m.setProductCode("AUTO"); m.setPlanCode("STANDARD"); m.setCoverageCode("COV");
        when(policyServiceProperties.getMappingForCoveragePlan(coveragePlanId)).thenReturn(Optional.of(m));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> service.createPolicy(req, customerId, null));
        verify(policyServiceAdapter, never()).createPolicyViaPolicyService(any(), anyLong(), anyString());
        verify(policyRepository, never()).save(any());
    }

    @Test
    void noMapping_fallsBackToLegacy_saveInvoked_and_adapterNotCalled() {
        // Arrange
        Long customerId = 11L;
        Long coveragePlanId = 3L;
        PolicyCreateRequest req = new PolicyCreateRequest(coveragePlanId, Instant.now(), Instant.now().plusSeconds(31536000));

        CoveragePlan cp = CoveragePlan.builder().id(coveragePlanId).name("Basic").productType("HOME").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(coveragePlanRepository.findById(coveragePlanId)).thenReturn(Optional.of(cp));
        when(policyServiceProperties.getMappingForCoveragePlan(coveragePlanId)).thenReturn(Optional.empty());

        Policy saved = Policy.builder().id(55L).policyNumber("POL-XYZ").status("PENDING").build();
        when(policyRepository.save(any())).thenReturn(saved);
        when(policyMapper.toPolicyResponse(any())).thenReturn(new PolicyResponse(55L, "POL-XYZ", "PENDING", "Basic", "HOME", req.effectiveDate(), req.renewalDate()));

        // Act
        PolicyResponse resp = service.createPolicy(req, customerId, null);

        // Assert
        assertNotNull(resp);
        assertEquals(55L, resp.id());
        verify(policyRepository, times(1)).save(any());
        verify(policyServiceAdapter, never()).createPolicyViaPolicyService(any(), anyLong(), anyString());
    }
}
