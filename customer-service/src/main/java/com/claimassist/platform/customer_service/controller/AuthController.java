package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.error.BadRequestException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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

    @PostMapping ("/signup")
    public ResponseEntity<Void> signup (@RequestBody @Valid SignupRequest request) {

        log.info ("Customer signup request received. username={}", request.username ());

        customerLookupService.findByUsername (request.username ())
                .ifPresent (customer -> {
                    throw new BadRequestException (
                            "A customer already exists with username: " + request.username ());
                });

        Customer customer = Customer.builder ()
                .username (request.username ())
                .fullName (request.fullName ())
                .build ();

        customer = customerRepository.save (customer);

        String keycloakUserId = keycloakUserProvisioningService.createUser (
                request.username (),
                request.fullName (),
                request.password (),
                customer.getId ());

        customer.setKeycloakId (keycloakUserId);

        customerRepository.save (customer);
        customerLookupService.evictByUsername (request.username ());

        log.info ("Customer registered successfully. customerId={}, keycloakId={}",
                customer.getId (),
                keycloakUserId);

        return ResponseEntity.status (HttpStatus.CREATED).build ();
    }

    @GetMapping ("/authorize")
    public ResponseEntity<Void> authorize () {

        OAuth2AuthorizationService.AuthorizationRequest request =
                authorizationService.createAuthorizationRequest ();

        return ResponseEntity.status (HttpStatus.FOUND)
                .location (URI.create (request.authorizationUrl ()))
                .build ();
    }

    @GetMapping ("/callback")
    public ResponseEntity<AuthResponse> callback (
            @RequestParam String code,
            @RequestParam String state) {

        String codeVerifier = authorizationService.consumeCodeVerifier (state);

        if (codeVerifier == null) {
            throw new BadRequestException ("Invalid or expired state parameter");
        }

        return ResponseEntity.ok (
                tokenService.exchangeAuthorizationCode (code, codeVerifier));
    }

    @PostMapping ("/refresh")
    public ResponseEntity<AuthResponse> refresh (
            @RequestParam String refreshToken) {

        return ResponseEntity.ok (
                tokenService.refreshToken (refreshToken));
    }

    @PostMapping ("/logout")
    public ResponseEntity<Void> logout (
            @RequestParam String refreshToken) {

        logoutService.logout (refreshToken);

        return ResponseEntity.noContent ().build ();
    }
}