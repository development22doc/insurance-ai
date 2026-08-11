package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.customer.CustomerResponse;
import com.claimassist.platform.customer_service.dto.customer.UpdateCustomerRequest;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerLookupService customerLookupService;
    private final KeycloakUserProvisioningService keycloakUserProvisioningService;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    /**
     * Updates a customer's profile information.
     * Updates both the local database and Keycloak user record.
     * Uses compensation pattern to ensure consistency.
     */
    @Transactional
    public CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request, Long requestingUserId) {
        long startTime = System.currentTimeMillis();

        // Authorization check: customer can only update their own profile
        if (!customerId.equals(requestingUserId)) {
            Map<String, Object> authFailedDetails = new HashMap<>();
            authFailedDetails.put("customerId", customerId);
            authFailedDetails.put("requestingUserId", requestingUserId);
            authFailedDetails.put("event", "CUSTOMER_UPDATE_AUTHORIZATION_FAILED");
            authFailedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            authFailedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", authFailedDetails);

            throw new BadRequestException("You can only update your own profile");
        }

        // Find the customer
        long findStart = System.currentTimeMillis();
        Optional<Customer> existingCustomerOpt = customerRepository.findById(customerId);
        if (existingCustomerOpt.isEmpty()) {
            long findDuration = System.currentTimeMillis() - findStart;
            Map<String, Object> notFoundDetails = new HashMap<>();
            notFoundDetails.put("customerId", customerId);
            notFoundDetails.put("event", "CUSTOMER_NOT_FOUND");
            notFoundDetails.put("executionTimeMs", findDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", notFoundDetails);

            throw new ResourceNotFoundException("Customer", String.valueOf(customerId));
        }
        Customer customer = existingCustomerOpt.get();
        long findDuration = System.currentTimeMillis() - findStart;

        Map<String, Object> updateStartDetails = new HashMap<>();
        updateStartDetails.put("customerId", customerId);
        updateStartDetails.put("event", "CUSTOMER_UPDATE_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", updateStartDetails);

        // Store old values for potential compensation
        String oldFullName = customer.getFullName();

        try {
            // Update database first
            long dbStartTime = System.currentTimeMillis();
            customer.setFullName(request.fullName());
            customer = customerRepository.save(customer);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            Map<String, Object> dbUpdateDetails = new HashMap<>();
            dbUpdateDetails.put("customerId", customerId);
            dbUpdateDetails.put("event", "CUSTOMER_DB_UPDATED");
            dbUpdateDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbUpdateDetails);
            performanceLogger.log("REPOSITORY", "repository.customer.update", dbDuration,
                    Map.of("customerId", customerId));

            // Update Keycloak user
            if (customer.getKeycloakId() != null) {
                long keycloakStartTime = System.currentTimeMillis();
                Map<String, Object> keycloakStartDetails = new HashMap<>();
                keycloakStartDetails.put("customerId", customerId);
                keycloakStartDetails.put("keycloakUserId", customer.getKeycloakId());
                keycloakStartDetails.put("event", "KEYCLOAK_USER_UPDATE_STARTED");
                eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakStartDetails);

                try {
                    keycloakUserProvisioningService.updateUser(
                            customer.getKeycloakId(),
                            request.fullName(),
                            customer.getUsername());

                    long keycloakDuration = System.currentTimeMillis() - keycloakStartTime;
                    Map<String, Object> keycloakUpdatedDetails = new HashMap<>();
                    keycloakUpdatedDetails.put("customerId", customerId);
                    keycloakUpdatedDetails.put("keycloakUserId", customer.getKeycloakId());
                    keycloakUpdatedDetails.put("event", "KEYCLOAK_USER_UPDATED");
                    keycloakUpdatedDetails.put("executionTimeMs", keycloakDuration);
                    eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakUpdatedDetails);
                    performanceLogger.log("BUSINESS", "keycloak.update_user", keycloakDuration,
                            Map.of("customerId", customerId, "keycloakUserId", customer.getKeycloakId()));
                } catch (ServiceUnavailableException keycloakFailure) {
                    // Compensation: revert database change
                    log.warn("Keycloak update failed, compensating by reverting database change for customerId={}",
                            customerId, keycloakFailure);
                    customer.setFullName(oldFullName);
                    customerRepository.save(customer);

                    Map<String, Object> compensationDetails = new HashMap<>();
                    compensationDetails.put("customerId", customerId);
                    compensationDetails.put("event", "KEYCLOAK_UPDATE_FAILED_COMPENSATED");
                    compensationDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                    compensationDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
                    eventLogger.logBusinessEvent("customer-service", "customer-service", compensationDetails);

                    throw new ServiceUnavailableException("Failed to update profile in identity provider. Please try again later.");
                }
            }

            // Evict cache
            customerLookupService.evictByUsername(customer.getUsername());

            // Emit completion event
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("customerId", customerId);
            completedDetails.put("event", "CUSTOMER_UPDATE_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "customer.update.total", totalDuration,
                    Map.of("customerId", customerId));

            return toResponse(customer);

        } catch (RuntimeException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> failedDetails = new HashMap<>();
            failedDetails.put("customerId", customerId);
            failedDetails.put("event", "CUSTOMER_UPDATE_FAILED");
            failedDetails.put("executionTimeMs", totalDuration);
            failedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            failedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", failedDetails);

            throw e;
        }
    }

    /**
     * Deletes a customer account.
     * This is a soft delete - the customer is marked as deleted but records are preserved for audit.
     * For a production system, this would typically involve:
     * 1. Checking for active policies/claims
     * 2. Deactivating the Keycloak user
     * 3. Soft delete in database
     * 4. Cache invalidation
     *
     * Note: This implementation uses hard delete for simplicity, but in production
     * you should implement soft delete based on business requirements.
     */
    @Transactional
    public void deleteCustomer(Long customerId, Long requestingUserId) {
        long startTime = System.currentTimeMillis();

        // Authorization check: customer can only delete their own account
        if (!customerId.equals(requestingUserId)) {
            Map<String, Object> authFailedDetails = new HashMap<>();
            authFailedDetails.put("customerId", customerId);
            authFailedDetails.put("requestingUserId", requestingUserId);
            authFailedDetails.put("event", "CUSTOMER_DELETE_AUTHORIZATION_FAILED");
            authFailedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            authFailedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", authFailedDetails);

            throw new BadRequestException("You can only delete your own account");
        }

        // Find the customer
        long findStart = System.currentTimeMillis();
        Optional<Customer> existingCustomerOpt = customerRepository.findById(customerId);
        if (existingCustomerOpt.isEmpty()) {
            long findDuration = System.currentTimeMillis() - findStart;
            Map<String, Object> notFoundDetails = new HashMap<>();
            notFoundDetails.put("customerId", customerId);
            notFoundDetails.put("event", "CUSTOMER_NOT_FOUND");
            notFoundDetails.put("executionTimeMs", findDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", notFoundDetails);

            throw new ResourceNotFoundException("Customer", String.valueOf(customerId));
        }
        Customer customer = existingCustomerOpt.get();
        long findDuration = System.currentTimeMillis() - findStart;

        Map<String, Object> deleteStartDetails = new HashMap<>();
        deleteStartDetails.put("customerId", customerId);
        deleteStartDetails.put("event", "CUSTOMER_DELETE_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", deleteStartDetails);

        String keycloakId = customer.getKeycloakId();
        String username = customer.getUsername();

        try {
            // Delete from database first
            long dbStartTime = System.currentTimeMillis();
            customerRepository.deleteById(customerId);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            Map<String, Object> dbDeleteDetails = new HashMap<>();
            dbDeleteDetails.put("customerId", customerId);
            dbDeleteDetails.put("event", "CUSTOMER_DB_DELETED");
            dbDeleteDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbDeleteDetails);
            performanceLogger.log("REPOSITORY", "repository.customer.delete", dbDuration,
                    Map.of("customerId", customerId));

            // Delete from Keycloak
            if (keycloakId != null) {
                long keycloakStartTime = System.currentTimeMillis();
                Map<String, Object> keycloakStartDetails = new HashMap<>();
                keycloakStartDetails.put("customerId", customerId);
                keycloakStartDetails.put("keycloakUserId", keycloakId);
                keycloakStartDetails.put("event", "KEYCLOAK_USER_DELETE_STARTED");
                eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakStartDetails);

                try {
                    keycloakUserProvisioningService.deleteUser(keycloakId);

                    long keycloakDuration = System.currentTimeMillis() - keycloakStartTime;
                    Map<String, Object> keycloakDeletedDetails = new HashMap<>();
                    keycloakDeletedDetails.put("customerId", customerId);
                    keycloakDeletedDetails.put("keycloakUserId", keycloakId);
                    keycloakDeletedDetails.put("event", "KEYCLOAK_USER_DELETED");
                    keycloakDeletedDetails.put("executionTimeMs", keycloakDuration);
                    eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakDeletedDetails);
                    performanceLogger.log("BUSINESS", "keycloak.delete_user", keycloakDuration,
                            Map.of("customerId", customerId, "keycloakUserId", keycloakId));
                } catch (ServiceUnavailableException keycloakFailure) {
                    // Log but don't fail - DB delete already succeeded
                    log.warn("Keycloak deletion failed for customerId={}, but DB delete succeeded. Manual cleanup may be required.",
                            customerId, keycloakFailure);

                    Map<String, Object> keycloakFailedDetails = new HashMap<>();
                    keycloakFailedDetails.put("customerId", customerId);
                    keycloakFailedDetails.put("keycloakUserId", keycloakId);
                    keycloakFailedDetails.put("event", "KEYCLOAK_DELETE_FAILED");
                    keycloakFailedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                    keycloakFailedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
                    eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakFailedDetails);
                }
            }

            // Evict cache
            customerLookupService.evictByUsername(username);

            // Emit completion event
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("customerId", customerId);
            completedDetails.put("event", "CUSTOMER_DELETE_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "customer.delete.total", totalDuration,
                    Map.of("customerId", customerId));

        } catch (RuntimeException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> failedDetails = new HashMap<>();
            failedDetails.put("customerId", customerId);
            failedDetails.put("event", "CUSTOMER_DELETE_FAILED");
            failedDetails.put("executionTimeMs", totalDuration);
            failedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            failedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", failedDetails);

            throw e;
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getUsername(),
                customer.getFullName(),
                customer.getKycStatus()
        );
    }
}
