package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.LogCategories;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.dto.auth.SignupRequest;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.service.CustomerLookupService;
import com.claimassist.platform.customer_service.service.KeycloakUserProvisioningService;
import com.claimassist.platform.customer_service.service.OAuth2AuthorizationService;
import com.claimassist.platform.customer_service.service.OAuth2LogoutService;
import com.claimassist.platform.customer_service.service.OAuth2TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping ("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final CustomerRepository customerRepository;
    private final CustomerLookupService customerLookupService;
    private final KeycloakUserProvisioningService keycloakUserProvisioningService;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenService tokenService;
    private final OAuth2LogoutService logoutService;
    private final EventLogger eventLogger;

    @PostMapping ("/signup")
    public ResponseEntity<Void> signup (@RequestBody @Valid SignupRequest request) {

        long startTime = System.currentTimeMillis();
        String username = request.username();

        // Emit SIGNUP_STARTED
        Map<String, Object> signupStartedDetails = new HashMap<>();
        signupStartedDetails.put("username", username);
        signupStartedDetails.put("event", "SIGNUP_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", signupStartedDetails);

        try {
            // Check if customer already exists
            Optional<Customer> existingCustomer = customerLookupService.findByUsername(username);

            if (existingCustomer.isPresent()) {
                Map<String, Object> dupeDetails = new HashMap<>();
                dupeDetails.put("username", username);
                dupeDetails.put("event", "CUSTOMER_ALREADY_EXISTS");
                dupeDetails.put("executionTimeMs", System.currentTimeMillis() - startTime);
                eventLogger.logSecurityEvent("customer-service", "customer-service", dupeDetails);

                throw new BadRequestException(
                        "A customer already exists with username: " + username);
            }

            // Emit VALIDATION_COMPLETED
            Map<String, Object> validationDetails = new HashMap<>();
            validationDetails.put("username", username);
            validationDetails.put("event", "SIGNUP_VALIDATION_COMPLETED");
            validationDetails.put("result", "valid");
            eventLogger.logBusinessEvent("customer-service", "customer-service", validationDetails);

            // Create customer in database
            long dbStartTime = System.currentTimeMillis();
            Map<String, Object> customerCreationStartedDetails = new HashMap<>();
            customerCreationStartedDetails.put("username", username);
            customerCreationStartedDetails.put("event", "CUSTOMER_CREATION_STARTED");
            eventLogger.logBusinessEvent("customer-service", "customer-service", customerCreationStartedDetails);

            Customer customer = Customer.builder ()
                    .username (username)
                    .fullName (request.fullName ())
                    .build ();

            customer = customerRepository.save (customer);

            long customerId = customer.getId();
            long dbDuration = System.currentTimeMillis() - dbStartTime;
            Map<String, Object> customerSavedDetails = new HashMap<>();
            customerSavedDetails.put("customerId", customerId);
            customerSavedDetails.put("username", username);
            customerSavedDetails.put("event", "CUSTOMER_SAVED");
            customerSavedDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, customerSavedDetails);

            // Create Keycloak user
            long keycloakStartTime = System.currentTimeMillis();
            Map<String, Object> keycloakStartDetails = new HashMap<>();
            keycloakStartDetails.put("customerId", customerId);
            keycloakStartDetails.put("username", username);
            keycloakStartDetails.put("event", "KEYCLOAK_USER_CREATION_STARTED");
            eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakStartDetails);

            String keycloakUserId = keycloakUserProvisioningService.createUser (
                    username,
                    request.fullName (),
                    request.password (),
                    customerId);

            long keycloakDuration = System.currentTimeMillis() - keycloakStartTime;
            Map<String, Object> keycloakCreatedDetails = new HashMap<>();
            keycloakCreatedDetails.put("customerId", customerId);
            keycloakCreatedDetails.put("keycloakUserId", keycloakUserId);
            keycloakCreatedDetails.put("username", username);
            keycloakCreatedDetails.put("event", "KEYCLOAK_USER_CREATED");
            keycloakCreatedDetails.put("executionTimeMs", keycloakDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", keycloakCreatedDetails);

            customer.setKeycloakId (keycloakUserId);
            customerRepository.save (customer);

            // Evict cache
            customerLookupService.evictByUsername (username);
            Map<String, Object> cacheEvictDetails = new HashMap<>();
            cacheEvictDetails.put("cacheKey", "customer:" + username);
            cacheEvictDetails.put("event", "CACHE_EVICT");
            eventLogger.logBusinessEvent("customer-service", "customer-service", cacheEvictDetails);

            // Emit SIGNUP_COMPLETED
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("customerId", customerId);
            completedDetails.put("keycloakUserId", keycloakUserId);
            completedDetails.put("username", username);
            completedDetails.put("event", "SIGNUP_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);

            return ResponseEntity.status (HttpStatus.CREATED).build ();

        } catch (BadRequestException e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> failureDetails = new HashMap<>();
            failureDetails.put("username", username);
            failureDetails.put("event", "SIGNUP_FAILED");
            failureDetails.put("reason", e.getMessage());
            failureDetails.put("executionTimeMs", duration);
            failureDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", failureDetails);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("username", username);
            errorDetails.put("event", "SIGNUP_FAILED");
            errorDetails.put("reason", e.getClass().getSimpleName());
            errorDetails.put("executionTimeMs", duration);
            errorDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", errorDetails);
            throw e;
        }
    }

    @GetMapping ("/authorize")
    public ResponseEntity<Void> authorize () {

        long startTime = System.currentTimeMillis();

        Map<String, Object> authStartDetails = new HashMap<>();
        authStartDetails.put("event", "AUTHORIZATION_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", authStartDetails);

        OAuth2AuthorizationService.AuthorizationRequest request =
                authorizationService.createAuthorizationRequest ();

        long duration = System.currentTimeMillis() - startTime;
        Map<String, Object> authCompleteDetails = new HashMap<>();
        authCompleteDetails.put("state", request.state());
        authCompleteDetails.put("event", "AUTHORIZATION_COMPLETED");
        authCompleteDetails.put("executionTimeMs", duration);
        eventLogger.logBusinessEvent("customer-service", "customer-service", authCompleteDetails);

        return ResponseEntity.status (HttpStatus.FOUND)
                .location (URI.create (request.authorizationUrl ()))
                .build ();
    }

    @GetMapping ("/callback")
    public ResponseEntity<AuthResponse> callback (
            @RequestParam String code,
            @RequestParam String state) {

        long startTime = System.currentTimeMillis();

        Map<String, Object> callbackStartDetails = new HashMap<>();
        callbackStartDetails.put("event", "CALLBACK_STARTED");
        callbackStartDetails.put("state", state);
        eventLogger.logBusinessEvent("customer-service", "customer-service", callbackStartDetails);

        try {
            String codeVerifier = authorizationService.consumeCodeVerifier (state);

            if (codeVerifier == null) {
                Map<String, Object> failDetails = new HashMap<>();
                failDetails.put("event", "CALLBACK_FAILED");
                failDetails.put("reason", "Invalid or expired state");
                failDetails.put("executionTimeMs", System.currentTimeMillis() - startTime);
                eventLogger.logSecurityEvent("customer-service", "customer-service", failDetails);

                throw new BadRequestException ("Invalid or expired state parameter");
            }

            long tokenStartTime = System.currentTimeMillis();
            Map<String, Object> tokenExchangeStartDetails = new HashMap<>();
            tokenExchangeStartDetails.put("event", "TOKEN_EXCHANGE_STARTED");
            eventLogger.logBusinessEvent("customer-service", "customer-service", tokenExchangeStartDetails);

            AuthResponse response = tokenService.exchangeAuthorizationCode (code, codeVerifier);

            long tokenDuration = System.currentTimeMillis() - tokenStartTime;
            Map<String, Object> tokenExchangeCompleteDetails = new HashMap<>();
            tokenExchangeCompleteDetails.put("event", "TOKEN_EXCHANGE_COMPLETED");
            tokenExchangeCompleteDetails.put("customerId", response.customerId());
            tokenExchangeCompleteDetails.put("executionTimeMs", tokenDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", tokenExchangeCompleteDetails);

            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> callbackCompleteDetails = new HashMap<>();
            callbackCompleteDetails.put("event", "CALLBACK_COMPLETED");
            callbackCompleteDetails.put("customerId", response.customerId());
            callbackCompleteDetails.put("executionTimeMs", totalDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", callbackCompleteDetails);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("event", "CALLBACK_FAILED");
            errorDetails.put("reason", e.getClass().getSimpleName());
            errorDetails.put("executionTimeMs", duration);
            errorDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", errorDetails);
            throw e;
        }
    }

    @PostMapping ("/refresh")
    public ResponseEntity<AuthResponse> refresh (
            @RequestParam String refreshToken) {

        long startTime = System.currentTimeMillis();

        Map<String, Object> refreshStartDetails = new HashMap<>();
        refreshStartDetails.put("event", "TOKEN_REFRESH_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", refreshStartDetails);

        try {
            AuthResponse response = tokenService.refreshToken (refreshToken);

            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> refreshCompleteDetails = new HashMap<>();
            refreshCompleteDetails.put("event", "TOKEN_REFRESH_COMPLETED");
            refreshCompleteDetails.put("customerId", response.customerId());
            refreshCompleteDetails.put("executionTimeMs", duration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", refreshCompleteDetails);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("event", "TOKEN_REFRESH_FAILED");
            errorDetails.put("reason", e.getClass().getSimpleName());
            errorDetails.put("executionTimeMs", duration);
            errorDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", errorDetails);
            throw e;
        }
    }

    @PostMapping ("/logout")
    public ResponseEntity<Void> logout (
            @RequestParam String refreshToken) {

        long startTime = System.currentTimeMillis();

        Map<String, Object> logoutStartDetails = new HashMap<>();
        logoutStartDetails.put("event", "LOGOUT_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", logoutStartDetails);

        try {
            logoutService.logout (refreshToken);

            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> logoutCompleteDetails = new HashMap<>();
            logoutCompleteDetails.put("event", "LOGOUT_COMPLETED");
            logoutCompleteDetails.put("executionTimeMs", duration);
            logoutCompleteDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", logoutCompleteDetails);

            return ResponseEntity.noContent ().build ();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("event", "LOGOUT_FAILED");
            errorDetails.put("reason", e.getClass().getSimpleName());
            errorDetails.put("executionTimeMs", duration);
            errorDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", errorDetails);
            throw e;
        }
    }
}
