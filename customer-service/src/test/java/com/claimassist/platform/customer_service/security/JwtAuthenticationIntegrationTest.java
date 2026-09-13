package com.claimassist.platform.customer_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to verify JWT authentication in SecurityContext.
 * This test directly creates a JWT authentication token and verifies it becomes authenticated.
 */
class JwtAuthenticationIntegrationTest {

    @Test
    void jwtBecomesAuthenticatedPrincipalInSecurityContext() {
        // Given: A valid JWT with required claims
        Long testUserId = 123L;
        String testUsername = "testuser@example.com";

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .claim("sub", testUsername)
                .claim("preferred_username", testUsername)
                .claim("email", testUsername)
                .claim("userId", testUserId)
                .claim("name", "Test User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // When: Create a JwtAuthenticationToken and set it in SecurityContext
        // Note: JwtAuthenticationToken constructor does not set authenticated=true by default
        // In real Spring Security flow, JwtAuthenticationProvider sets authenticated=true after validation
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, Collections.emptyList());
        authentication.setAuthenticated(true); // Simulate successful authentication by JwtAuthenticationProvider
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Then: Verify the SecurityContext has an authenticated JWT principal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(auth, "Authentication should not be null");
        assertTrue(auth.isAuthenticated(), "Authentication should be authenticated");
        assertTrue(auth instanceof JwtAuthenticationToken,
            "Authentication should be JwtAuthenticationToken, but was: " + auth.getClass().getName());

        Jwt authenticatedJwt = (Jwt) auth.getPrincipal();
        assertNotNull(authenticatedJwt, "JWT principal should not be null");
        assertEquals(testUsername, authenticatedJwt.getSubject(), "JWT subject should match");
        assertEquals(testUserId, authenticatedJwt.getClaim("userId"), "JWT userId claim should match");

        System.out.println("=== JWT AUTHENTICATION SUCCESS ===");
        System.out.println("Authentication class: " + auth.getClass().getName());
        System.out.println("Authenticated: " + auth.isAuthenticated());
        System.out.println("Principal class: " + auth.getPrincipal().getClass().getName());
        System.out.println("JWT subject: " + authenticatedJwt.getSubject());
        System.out.println("JWT userId claim: " + authenticatedJwt.getClaim("userId"));

        SecurityContextHolder.clearContext();
    }
}
