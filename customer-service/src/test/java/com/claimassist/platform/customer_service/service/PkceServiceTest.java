package com.claimassist.platform.customer_service.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PKCE primitives: high-entropy verifier, correct S256 challenge, distinct state.
 */
class PkceServiceTest {

    private final PkceService pkceService = new PkceService();

    @Test
    void codeVerifierIs64CharsBase64Url() {
        String verifier = pkceService.generateCodeVerifier();
        assertThat(verifier).hasSize(64);
        assertThat(verifier).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void codeVerifierIsHighEntropy() {
        assertThat(pkceService.generateCodeVerifier()).isNotEqualTo(pkceService.generateCodeVerifier());
    }

    @Test
    void codeChallengeMatchesS256OfVerifier() throws Exception {
        String verifier = pkceService.generateCodeVerifier();
        String challenge = pkceService.generateCodeChallenge(verifier);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        assertThat(challenge).isEqualTo(expected);
    }

    @Test
    void statesAreDistinctAndOpaque() {
        assertThat(pkceService.generateState()).isNotEqualTo(pkceService.generateState());
        assertThat(pkceService.generateState()).matches("^[A-Za-z0-9_-]+$");
    }
}