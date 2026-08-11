package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.auth.SignupRequest;
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
public class CustomerSignupService {

    private final CustomerRepository customerRepository;
    private final CustomerLookupService customerLookupService;
    private final KeycloakUserProvisioningService keycloakUserProvisioningService;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    @Transactional
    public void signup(SignupRequest request) {
        long startTime = System.currentTimeMillis();
        String username = request.username();

        Map<String, Object> signupStartedDetails = new HashMap<>();
        signupStartedDetails.put("username", username);
        signupStartedDetails.put("event", "SIGNUP_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", signupStartedDetails);

        // Validate customer uniqueness
        long validationStart = System.currentTimeMillis();
        Optional<Customer> existingCustomer = customerLookupService.findByUsername(username);
        if (existingCustomer.isPresent()) {
            long validationDuration = System.currentTimeMillis() - validationStart;
            Map<String, Object> dupeDetails = new HashMap<>();
            dupeDetails.put("username", username);
            dupeDetails.put("event", "CUSTOMER_ALREADY_EXISTS");
            dupeDetails.put("executionTimeMs", validationDuration);
            eventLogger.logSecurityEvent("customer-service", "customer-service", dupeDetails);

            throw new BadRequestException("A customer already exists with username: " + username);
        }

        // Create customer in database first to obtain the numeric id needed for Keycloak "legacy_user_id".
        // We rely on explicit compensation + transaction rollback to ensure a failed signup does not persist
        // any customer row.
        long dbStartTime = System.currentTimeMillis();
        Map<String, Object> customerCreationStartedDetails = new HashMap<>();
        customerCreationStartedDetails.put("username", username);
        customerCreationStartedDetails.put("event", "CUSTOMER_CREATION_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", customerCreationStartedDetails);

        Customer customer = Customer.builder()
                .username(username)
                .fullName(request.fullName())
                .build();
        customer = customerRepository.save(customer);
        long customerId = customer.getId();
        long dbDuration = System.currentTimeMillis() - dbStartTime;

        try {
            // Create Keycloak user
            long keycloakStartTime = System.currentTimeMillis();
            Map<String, Object> keycloakStartDetails = new HashMap<>();
            keycloakStartDetails.put("customerId", customerId);
            keycloakStartDetails.put("username", username);
            keycloakStartDetails.put("event", "KEYCLOAK_USER_CREATION_STARTED");
            eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakStartDetails);

            String keycloakUserId = keycloakUserProvisioningService.createUser(
                    username,
                    request.fullName(),
                    request.password(),
                    customerId);

            long keycloakDuration = System.currentTimeMillis() - keycloakStartTime;

            Map<String, Object> keycloakCreatedDetails = new HashMap<>();
            keycloakCreatedDetails.put("customerId", customerId);
            keycloakCreatedDetails.put("keycloakUserId", keycloakUserId);
            keycloakCreatedDetails.put("username", username);
            keycloakCreatedDetails.put("event", "KEYCLOAK_USER_CREATED");
            keycloakCreatedDetails.put("executionTimeMs", keycloakDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakCreatedDetails);
            performanceLogger.log("BUSINESS", "keycloak.create_user", keycloakDuration,
                    Map.of("username", username, "customerId", customerId));

            // Only after Keycloak user creation succeeds do we log "CUSTOMER_SAVED" and persist the keycloakId.
            customer.setKeycloakId(keycloakUserId);
            long updateStartTime = System.currentTimeMillis();
            try {
                customerRepository.save(customer);
            } catch (RuntimeException dbFailure) {
                // Compensation: Keycloak user already exists; delete it to avoid orphan.
                Map<String, Object> saveFailDetails = new HashMap<>();
                saveFailDetails.put("customerId", customerId);
                saveFailDetails.put("keycloakUserId", keycloakUserId);
                saveFailDetails.put("event", "CUSTOMER_SAVE_FAILED");
                long execMs = System.currentTimeMillis() - updateStartTime;
                saveFailDetails.put("executionTimeMs", execMs);
                eventLogger.logBusinessEvent("customer-service", "customer-service", saveFailDetails);

                Map<String, Object> compensationStart = new HashMap<>();
                compensationStart.put("event", "KEYCLOAK_COMPENSATION_STARTED");
                compensationStart.put("customerId", customerId);
                compensationStart.put("keycloakUserId", keycloakUserId);
                compensationStart.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            compensationStart.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
                eventLogger.logBusinessEvent("customer-service", "customer-service", compensationStart);

                try {
                    keycloakUserProvisioningService.deleteUser(keycloakUserId);

                    Map<String, Object> compensatedDetails = new HashMap<>();
                    compensatedDetails.put("event", "KEYCLOAK_USER_COMPENSATED");
                    compensatedDetails.put("customerId", customerId);
                    compensatedDetails.put("keycloakUserId", keycloakUserId);
                    compensatedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                    compensatedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
                    eventLogger.logBusinessEvent("customer-service", "customer-service", compensatedDetails);
                } catch (Exception compensationFailure) {
                    // Intentionally do not mask the original DB failure; we only best-effort compensate.
                    log.warn("Keycloak compensation (deleteUser) failed for keycloakUserId={}",
                            keycloakUserId, compensationFailure);
                }

                Map<String, Object> signupFailed = new HashMap<>();
                signupFailed.put("event", "SIGNUP_FAILED");
                signupFailed.put("customerId", customerId);
                signupFailed.put("username", username);
                signupFailed.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                signupFailed.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
                eventLogger.logBusinessEvent("customer-service", "customer-service", signupFailed);

                throw dbFailure;
            }

            long updateDuration = System.currentTimeMillis() - updateStartTime;
            Map<String, Object> customerSavedDetails = new HashMap<>();
            customerSavedDetails.put("customerId", customerId);
            customerSavedDetails.put("username", username);
            customerSavedDetails.put("event", "CUSTOMER_SAVED");
            customerSavedDetails.put("executionTimeMs", dbDuration + updateDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration + updateDuration, customerSavedDetails);
            performanceLogger.log("REPOSITORY", "repository.customer.save", dbDuration + updateDuration,
                    Map.of("username", username, "customerId", customerId));

            // Evict cache
            customerLookupService.evictByUsername(username);

            // Emit SIGNUP_COMPLETED
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("customerId", customerId);
            completedDetails.put("keycloakUserId", keycloakUserId);
            completedDetails.put("username", username);
            completedDetails.put("event", "SIGNUP_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "signup.total", totalDuration,
                    Map.of("username", username, "customerId", customerId));
        } catch (ServiceUnavailableException keycloakFailure) {
            // Keycloak token acquisition or provisioning failed.
            // Compensation: remove the customer row so failed signup never leaves an orphan DB record.
            try {
                customerRepository.deleteById(customerId);
            } catch (DataAccessException ignored) {
                // Best-effort cleanup; the transaction rollback will also prevent persistence.
            }

            Map<String, Object> signupFailed = new HashMap<>();
            signupFailed.put("event", "SIGNUP_FAILED");
            signupFailed.put("customerId", customerId);
            signupFailed.put("username", username);
            signupFailed.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            signupFailed.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", signupFailed);

            throw keycloakFailure;
        }
    }
}

