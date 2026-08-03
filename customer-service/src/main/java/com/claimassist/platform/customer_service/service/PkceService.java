package com.claimassist.platform.customer_service.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PkceService {

    private static final int CODE_VERIFIER_LENGTH = 64;

    private final SecureRandom secureRandom = new SecureRandom ();

    /**
     * Generates a high-entropy PKCE code verifier.
     */
    public String generateCodeVerifier () {

        byte[] bytes = new byte[CODE_VERIFIER_LENGTH];

        secureRandom.nextBytes (bytes);

        return Base64.getUrlEncoder ()
                .withoutPadding ()
                .encodeToString (bytes);
    }

    /**
     * Generates the PKCE S256 code challenge.
     */
    public String generateCodeChallenge (String codeVerifier) {

        try {

            MessageDigest digest = MessageDigest.getInstance ("SHA-256");

            byte[] hash = digest.digest (
                    codeVerifier.getBytes (StandardCharsets.US_ASCII));

            return Base64.getUrlEncoder ()
                    .withoutPadding ()
                    .encodeToString (hash);

        } catch (Exception ex) {

            throw new IllegalStateException (
                    "Unable to generate PKCE code challenge.",
                    ex
            );
        }
    }

    /**
     * Generates OAuth2 state parameter.
     */
    public String generateState () {

        byte[] bytes = new byte[32];

        secureRandom.nextBytes (bytes);

        return Base64.getUrlEncoder ()
                .withoutPadding ()
                .encodeToString (bytes);
    }
}