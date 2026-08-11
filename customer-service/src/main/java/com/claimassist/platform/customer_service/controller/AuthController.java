package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.LogCategories;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.dto.auth.SignupRequest;
import com.claimassist.platform.customer_service.service.CustomerSignupService;
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

    private final CustomerSignupService customerSignupService;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenService tokenService;
    private final OAuth2LogoutService logoutService;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    @PostMapping ("/signup")
    public ResponseEntity<Void> signup (@RequestBody @Valid SignupRequest request) {
        customerSignupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
     }

     @GetMapping ("/authorize")
    public ResponseEntity<Void> authorize () {

        long startTime = System.currentTimeMillis();

        Map<String, Object> authStartDetails = new HashMap<>();
        authStartDetails.put("event", "AUTHORIZATION_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", authStartDetails);

        try {
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
        } catch (Exception e) {
            log.error("Error in authorize endpoint", e);
            throw e;
        }
    }

    @GetMapping ("/callback")
    public ResponseEntity<AuthResponse> callback (
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            @RequestParam String state) {

        long startTime = System.currentTimeMillis();

        Map<String, Object> callbackStartDetails = new HashMap<>();
        callbackStartDetails.put("event", "CALLBACK_STARTED");
        callbackStartDetails.put("state", state);
        eventLogger.logBusinessEvent("customer-service", "customer-service", callbackStartDetails);

        try {
            // Handle OAuth error response from Keycloak
            if (error != null && !error.isEmpty()) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("event", "CALLBACK_ERROR");
                errorDetails.put("error", error);
                errorDetails.put("error_description", error_description != null ? error_description : "Unknown error");
                errorDetails.put("state", state);
                eventLogger.logBusinessEvent("customer-service", "customer-service", errorDetails);

                throw new BadRequestException (
                        "Authorization failed: " + error + " - " + (error_description != null ? error_description : "Unknown error"));
            }

            // Handle success response
            if (code == null || code.isEmpty()) {
                throw new BadRequestException ("Missing authorization code");
            }

            String codeVerifier = authorizationService.consumeCodeVerifier (state);

            if (codeVerifier == null) {
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

             Map<String, Object> tokenExchangePerfDetails = new HashMap<>();
             tokenExchangePerfDetails.put("status", "token_exchange_completed");
             if (response.customerId() != null) {
                 tokenExchangePerfDetails.put("customerId", response.customerId());
             }
             performanceLogger.log("BUSINESS", "keycloak.token.exchange", tokenDuration, tokenExchangePerfDetails);

             long totalDuration = System.currentTimeMillis() - startTime;
             Map<String, Object> callbackCompleteDetails = new HashMap<>();
             callbackCompleteDetails.put("event", "CALLBACK_COMPLETED");
             callbackCompleteDetails.put("customerId", response.customerId());
             callbackCompleteDetails.put("executionTimeMs", totalDuration);
             eventLogger.logBusinessEvent("customer-service", "customer-service", callbackCompleteDetails);

             Map<String, Object> callbackPerfDetails = new HashMap<>();
             callbackPerfDetails.put("status", "callback_completed");
             if (response.customerId() != null) {
                 callbackPerfDetails.put("customerId", response.customerId());
             }
             performanceLogger.log("BUSINESS", "oauth.callback.total", totalDuration, callbackPerfDetails);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
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

             long totalDuration = System.currentTimeMillis() - startTime;
             Map<String, Object> refreshCompleteDetails = new HashMap<>();
             refreshCompleteDetails.put("event", "TOKEN_REFRESH_COMPLETED");
             refreshCompleteDetails.put("customerId", response.customerId());
             refreshCompleteDetails.put("executionTimeMs", totalDuration);
             eventLogger.logBusinessEvent("customer-service", "customer-service", refreshCompleteDetails);

             Map<String, Object> refreshPerfDetails = new HashMap<>();
             refreshPerfDetails.put("status", "refresh_completed");
             if (response.customerId() != null) {
                 refreshPerfDetails.put("customerId", response.customerId());
             }
             performanceLogger.log("BUSINESS", "refresh.token.total", totalDuration, refreshPerfDetails);

             return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw e;
        }
    }

     @PostMapping ("/logout")
     public ResponseEntity<Void> logout (
             @RequestParam String refreshToken) {

         long startTime = System.currentTimeMillis();

         Map<String, Object> logoutStartDetails = new HashMap<>();
         logoutStartDetails.put("event", "LOGOUT_STARTED");
         logoutStartDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
         eventLogger.logBusinessEvent("customer-service", "customer-service", logoutStartDetails);

        logoutService.logout (refreshToken);

        long totalDuration = System.currentTimeMillis() - startTime;
        Map<String, Object> logoutCompleteDetails = new HashMap<>();
        logoutCompleteDetails.put("event", "LOGOUT_COMPLETED");
        logoutCompleteDetails.put("executionTimeMs", totalDuration);
        logoutCompleteDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
        eventLogger.logBusinessEvent("customer-service", "customer-service", logoutCompleteDetails);
        performanceLogger.log("BUSINESS", "logout.total", totalDuration,
                Map.of());

        return ResponseEntity.noContent ().build ();
    }
}
