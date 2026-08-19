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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private CustomerRepository customerRepository;
    private CustomerLookupService customerLookupService;
    private KeycloakUserProvisioningService keycloakUserProvisioningService;
    private CustomerService service;

    private Customer customer(Long id, String keycloakId) {
        return Customer.builder().id(id).username("alice@example.com")
                .fullName("Alice A").keycloakId(keycloakId).build();
    }

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        customerLookupService = mock(CustomerLookupService.class);
        keycloakUserProvisioningService = mock(KeycloakUserProvisioningService.class);
        service = new CustomerService(customerRepository, customerLookupService,
                keycloakUserProvisioningService, mock(EventLogger.class), mock(PerformanceLogger.class));

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updateCustomerRejectsUpdatingAnotherUsersProfile() {
        assertThatThrownBy(() -> service.updateCustomer(1L, new UpdateCustomerRequest("Bob"), 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only update your own");
    }

    @Test
    void updateCustomerThrowsWhenCustomerMissing() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateCustomer(1L, new UpdateCustomerRequest("Bob"), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCustomerWithoutKeycloakUpdatesAndEvictsCache() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, null)));

        CustomerResponse response = service.updateCustomer(1L, new UpdateCustomerRequest("Bob B"), 1L);

        assertThat(response.fullName()).isEqualTo("Bob B");
        verify(customerRepository).save(any(Customer.class));
        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void updateCustomerWithKeycloakPropagatesChange() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "kc-1")));

        CustomerResponse response = service.updateCustomer(1L, new UpdateCustomerRequest("Bob B"), 1L);

        verify(keycloakUserProvisioningService).updateUser(eq("kc-1"), eq("Bob B"), eq("alice@example.com"));
        assertThat(response.fullName()).isEqualTo("Bob B");
    }

    @Test
    void updateCustomerCompensatesWhenKeycloakFails() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "kc-1")));
        doThrow(new ServiceUnavailableException("down"))
                .when(keycloakUserProvisioningService)
                .updateUser(eq("kc-1"), any(), any());

        assertThatThrownBy(() -> service.updateCustomer(1L, new UpdateCustomerRequest("Bob B"), 1L))
                .isInstanceOf(ServiceUnavailableException.class);

        // Compensation: initial save + revert save both happened
        verify(customerRepository, times(2)).save(any(Customer.class));
    }

    @Test
    void deleteCustomerRejectsDeletingAnotherUsersAccount() {
        assertThatThrownBy(() -> service.deleteCustomer(1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteCustomerThrowsWhenCustomerMissing() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteCustomer(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCustomerDeletesAndEvictsCache() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "kc-1")));

        service.deleteCustomer(1L, 1L);

        verify(customerRepository).deleteById(1L);
        verify(keycloakUserProvisioningService).deleteUser("kc-1");
        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void deleteCustomerSwallowsKeycloakFailureAfterDbDelete() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "kc-1")));
        doThrow(new ServiceUnavailableException("down"))
                .when(keycloakUserProvisioningService).deleteUser("kc-1");

        service.deleteCustomer(1L, 1L);

        verify(customerRepository).deleteById(1L);
    }
}
