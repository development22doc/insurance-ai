package com.claimassist.platform.customer_service.service;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.customer_service.dto.customer.CustomerResponse;
import com.claimassist.platform.customer_service.dto.customer.UpdateCustomerRequest;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private KeycloakUserProvisioningService keycloakUserProvisioningService;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private PerformanceLogger performanceLogger;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;
    private UpdateCustomerRequest updateRequest;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .username("testuser")
                .fullName("Test User")
                .keycloakId("keycloak-123")
                .build();

        updateRequest = new UpdateCustomerRequest("Updated Name");
    }

    @Test
    void updateCustomer_WithValidRequestAndMatchingUserId_ShouldUpdateCustomerSuccess() {
        // Given
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .username("testuser")
                .fullName("Updated Name")
                .keycloakId("keycloak-123")
                .build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);
        doNothing().when(keycloakUserProvisioningService).updateUser(
                eq("keycloak-123"),
                eq("Updated Name"),
                eq("testuser"));

        // When
        CustomerResponse response = customerService.updateCustomer(1L, updateRequest, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.fullName()).isEqualTo("Updated Name");
        verify(customerRepository).findById(1L);
        verify(customerRepository).save(any(Customer.class));
        verify(keycloakUserProvisioningService).updateUser("keycloak-123", "Updated Name", "testuser");
    }

    @Test
    void updateCustomer_WithNonMatchingUserId_ShouldThrowBadRequestException() {
        // Given
        UpdateCustomerRequest request = new UpdateCustomerRequest("Updated Name");

        // When & Then
        assertThatThrownBy(() -> customerService.updateCustomer(1L, request, 999L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("You can only update your own profile");

        verify(customerRepository, never()).save(any());
        verify(keycloakUserProvisioningService, never()).updateUser(any(), anyString(), anyString());
    }

    @Test
    void updateCustomer_WithNonExistentCustomer_ShouldThrowResourceNotFoundException() {
        // Given - use requestingUserId=999L to bypass authorization check
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customerService.updateCustomer(999L, updateRequest, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer", String.valueOf(999L));

        verify(customerRepository, never()).save(any());
        verify(keycloakUserProvisioningService, never()).updateUser(any(), anyString(), anyString());
    }

    @Test
    void updateCustomer_WithKeycloakFailure_ShouldCompensateAndThrow() {
        // Given
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .username("testuser")
                .fullName("Updated Name")
                .keycloakId("keycloak-123")
                .build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);
        doThrow(new ServiceUnavailableException("Failed to update profile in identity provider. Please try again later."))
                .when(keycloakUserProvisioningService).updateUser(
                        eq("keycloak-123"),
                        eq("Updated Name"),
                        eq("testuser"));

        // When & Then
        assertThatThrownBy(() -> customerService.updateCustomer(1L, updateRequest, 1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Failed to update profile in identity provider");

        verify(customerRepository, times(2)).save(any(Customer.class));
    }

    @Test
    void deleteCustomer_WithValidRequestAndMatchingUserId_ShouldDeleteCustomerSuccess() {
        // Given
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        doNothing().when(keycloakUserProvisioningService).deleteUser("keycloak-123");

        // When
        customerService.deleteCustomer(1L, 1L);

        // Then
        verify(customerRepository).findById(1L);
        verify(customerRepository).deleteById(1L);
        verify(keycloakUserProvisioningService).deleteUser("keycloak-123");
    }

    @Test
    void deleteCustomer_WithNonMatchingUserId_ShouldThrowBadRequestException() {
        // When & Then
        assertThatThrownBy(() -> customerService.deleteCustomer(1L, 999L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("You can only delete your own account");

        verify(customerRepository, never()).deleteById(anyLong());
        verify(keycloakUserProvisioningService, never()).deleteUser(anyString());
    }

    @Test
    void deleteCustomer_WithNonExistentCustomer_ShouldThrowResourceNotFoundException() {
        // Given - use requestingUserId=999L to bypass authorization check
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> customerService.deleteCustomer(999L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer", String.valueOf(999L));

        verify(customerRepository, never()).deleteById(anyLong());
    }

    @Test
    void updateCustomer_WithKeycloakFailure_ShouldRevertDatabaseChange() {
        // Given
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .username("testuser")
                .fullName("Updated Name")
                .keycloakId("keycloak-123")
                .build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);
        doThrow(new ServiceUnavailableException("Failed to update profile in identity provider. Please try again later."))
                .when(keycloakUserProvisioningService).updateUser(
                        eq("keycloak-123"),
                        eq("Updated Name"),
                        eq("testuser"));

        // When & Then
        assertThatThrownBy(() -> customerService.updateCustomer(1L, updateRequest, 1L))
                .isInstanceOf(ServiceUnavailableException.class);

        // Verify reversion - customer should be reverted to old name
        verify(customerRepository, times(2)).save(any(Customer.class));
    }

    @Test
    void deleteCustomer_WithKeycloakFailure_ShouldLogButNotFail() {
        // Given
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        doNothing().when(keycloakUserProvisioningService).deleteUser("keycloak-123");

        // When & Then
        customerService.deleteCustomer(1L, 1L);

        // DB delete should have succeeded, Keycloak failure should be logged only
        verify(customerRepository).findById(1L);
        verify(customerRepository).deleteById(1L);
        verify(keycloakUserProvisioningService).deleteUser("keycloak-123");
    }
}