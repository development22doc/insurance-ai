package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.customer.CustomerResponse;
import com.claimassist.platform.customer_service.dto.customer.UpdateCustomerRequest;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerLookupService customerLookupService;
    @Mock private KeycloakUserProvisioningService keycloakUserProvisioningService;
    @Mock private EventLogger eventLogger;
    @Mock private PerformanceLogger performanceLogger;

    @InjectMocks private CustomerService customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1L).username("alice@example.com").fullName("Alice").keycloakId("kc-1").build();
    }

    @Test
    void updateCustomer_authorizationMismatch_throwsBadRequest() {
        assertThatThrownBy(() -> customerService.updateCustomer(1L,
                new UpdateCustomerRequest("Alice Smith"), 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own profile");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_notFound_throwsResourceNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(1L,
                new UpdateCustomerRequest("Alice Smith"), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCustomer_success_withKeycloakSync() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse result = customerService.updateCustomer(1L,
                new UpdateCustomerRequest("Alice Smith"), 1L);

        assertThat(result.fullName()).isEqualTo("Alice Smith");
        verify(keycloakUserProvisioningService).updateUser("kc-1", "Alice Smith", "alice@example.com");
        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void updateCustomer_noKeycloakId_skipsKeycloakSync() {
        customer.setKeycloakId(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.updateCustomer(1L, new UpdateCustomerRequest("Alice Smith"), 1L);

        verify(keycloakUserProvisioningService, never()).updateUser(any(), any(), any());
    }

    @Test
    void updateCustomer_keycloakFailure_compensatesAndThrows() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ServiceUnavailableException("down")).when(keycloakUserProvisioningService)
                .updateUser(any(), any(), any());

        assertThatThrownBy(() -> customerService.updateCustomer(1L,
                new UpdateCustomerRequest("Alice Smith"), 1L))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(customerRepository, org.mockito.Mockito.times(2)).save(any(Customer.class));
        assertThat(customer.getFullName()).isEqualTo("Alice");
    }

    @Test
    void deleteCustomer_authorizationMismatch_throwsBadRequest() {
        assertThatThrownBy(() -> customerService.deleteCustomer(1L, 99L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteCustomer_notFound_throwsResourceNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.deleteCustomer(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCustomer_success_withKeycloakSync() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.deleteCustomer(1L, 1L);

        verify(customerRepository).deleteById(1L);
        verify(keycloakUserProvisioningService).deleteUser("kc-1");
        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void deleteCustomer_keycloakFailure_doesNotFail() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        doThrow(new ServiceUnavailableException("down")).when(keycloakUserProvisioningService).deleteUser("kc-1");

        customerService.deleteCustomer(1L, 1L);

        verify(customerRepository).deleteById(1L);
        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void deleteCustomer_noKeycloakId_skipsKeycloak() {
        customer.setKeycloakId(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.deleteCustomer(1L, 1L);

        verify(keycloakUserProvisioningService, never()).deleteUser(any());
    }
}