package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.customer_service.dto.auth.SignupRequest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSignupServiceTest {

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
    private CustomerSignupService customerSignupService;

    private SignupRequest signupRequest;

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest("testuser", "Test User", "password123");
    }

    @Test
    void signup_WithValidRequest_ShouldCreateCustomerAndKeycloakUser() {
        // Given
        when(customerLookupService.findByUsername("testuser")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return customer;
        });
        when(keycloakUserProvisioningService.createUser(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("keycloak-user-id-123");

        // When
        customerSignupService.signup(signupRequest);

        // Then
        verify(customerLookupService).findByUsername("testuser");
        verify(customerRepository, times(2)).save(any(Customer.class)); // Once for creation, once for update
        verify(keycloakUserProvisioningService).createUser("testuser", "Test User", "password123", 1L);
        verify(customerLookupService).evictByUsername("testuser");
    }

    @Test
    void signup_WithDuplicateUsername_ShouldThrowBadRequestException() {
        // Given
        Customer existingCustomer = Customer.builder()
                .id(1L)
                .username("testuser")
                .fullName("Existing User")
                .build();
        when(customerLookupService.findByUsername("testuser")).thenReturn(Optional.of(existingCustomer));

        // When & Then
        assertThatThrownBy(() -> customerSignupService.signup(signupRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A customer already exists with username: testuser");

        verify(customerRepository, never()).save(any());
        verify(keycloakUserProvisioningService, never()).createUser(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void signup_WhenKeycloakFails_ShouldRollbackCustomer() {
        // Given
        when(customerLookupService.findByUsername("testuser")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return customer;
        });
        when(keycloakUserProvisioningService.createUser(anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new ServiceUnavailableException("Keycloak unavailable"));

        // When & Then
        assertThatThrownBy(() -> customerSignupService.signup(signupRequest))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Keycloak unavailable");

        // Verify compensation - customer should be deleted
        verify(customerRepository).deleteById(1L);
    }

    @Test
    void signup_WhenDatabaseUpdateFails_ShouldCompensateKeycloakUser() {
        // Given
        when(customerLookupService.findByUsername("testuser")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    customer.setId(1L);
                    return customer;
                })
                .thenThrow(new RuntimeException("Database connection failed"));
        when(keycloakUserProvisioningService.createUser(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("keycloak-user-id-123");

        // When & Then
        assertThatThrownBy(() -> customerSignupService.signup(signupRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");

        // Verify compensation - Keycloak user should be deleted
        verify(keycloakUserProvisioningService).deleteUser("keycloak-user-id-123");
    }

    @Test
    void signup_WhenKeycloakCompensationFails_ShouldStillThrowOriginalException() {
        // Given
        when(customerLookupService.findByUsername("testuser")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    customer.setId(1L);
                    return customer;
                })
                .thenThrow(new RuntimeException("Database connection failed"));
        when(keycloakUserProvisioningService.createUser(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("keycloak-user-id-123");
        doThrow(new RuntimeException("Compensation failed"))
                .when(keycloakUserProvisioningService).deleteUser(anyString());

        // When & Then
        assertThatThrownBy(() -> customerSignupService.signup(signupRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");

        // Original exception should be thrown, not the compensation failure
        verify(keycloakUserProvisioningService).deleteUser("keycloak-user-id-123");
    }

    @Test
    void signup_WithNullRequest_ShouldThrowNullPointerException() {
        // When & Then
        assertThatThrownBy(() -> customerSignupService.signup(null))
                .isInstanceOf(NullPointerException.class);
    }
}
