package com.claimassist.platform.customer_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PkceServiceTest {

    private PkceService pkceService;

    @BeforeEach
    void setUp() {
        pkceService = new PkceService();
    }

    @Test
    void generateCodeVerifier_ShouldGenerateRandomBase64String() {
        // When
        String verifier1 = pkceService.generateCodeVerifier();
        String verifier2 = pkceService.generateCodeVerifier();

        // Then
        assertThat(verifier1).isNotEmpty();
        assertThat(verifier2).isNotEmpty();
        assertThat(verifier1).isNotEqualTo(verifier2); // Should be random
        assertThat(verifier1).doesNotContain("="); // No padding
        assertThat(verifier1).doesNotContain("+"); // URL-safe encoding
        assertThat(verifier1).doesNotContain("/"); // URL-safe encoding
    }

    @Test
    void generateCodeVerifier_ShouldHaveConsistentLength() {
        // When
        String verifier = pkceService.generateCodeVerifier();

        // Then - 64 bytes encoded in base64 without padding = 86 characters
        assertThat(verifier).hasSize(86);
    }

    @Test
    void generateCodeChallenge_ShouldGenerateS256Challenge() {
        // Given
        String codeVerifier = pkceService.generateCodeVerifier();

        // When
        String challenge = pkceService.generateCodeChallenge(codeVerifier);

        // Then
        assertThat(challenge).isNotEmpty();
        assertThat(challenge).doesNotContain("="); // No padding
        assertThat(challenge).doesNotContain("+"); // URL-safe encoding
        assertThat(challenge).doesNotContain("/"); // URL-safe encoding
        assertThat(challenge).isNotEqualTo(codeVerifier); // Challenge should be different from verifier
    }

    @Test
    void generateCodeChallenge_ShouldBeDeterministicForSameVerifier() {
        // Given
        String codeVerifier = pkceService.generateCodeVerifier();

        // When
        String challenge1 = pkceService.generateCodeChallenge(codeVerifier);
        String challenge2 = pkceService.generateCodeChallenge(codeVerifier);

        // Then - SHA-256 is deterministic
        assertThat(challenge1).isEqualTo(challenge2);
    }

    @Test
    void generateCodeChallenge_ShouldProduceDifferentChallengesForDifferentVerifiers() {
        // Given
        String verifier1 = pkceService.generateCodeVerifier();
        String verifier2 = pkceService.generateCodeVerifier();

        // When
        String challenge1 = pkceService.generateCodeChallenge(verifier1);
        String challenge2 = pkceService.generateCodeChallenge(verifier2);

        // Then
        assertThat(challenge1).isNotEqualTo(challenge2);
    }

    @Test
    void generateState_ShouldGenerateRandomBase64String() {
        // When
        String state1 = pkceService.generateState();
        String state2 = pkceService.generateState();

        // Then
        assertThat(state1).isNotEmpty();
        assertThat(state2).isNotEmpty();
        assertThat(state1).isNotEqualTo(state2); // Should be random
        assertThat(state1).doesNotContain("="); // No padding
        assertThat(state1).doesNotContain("+"); // URL-safe encoding
        assertThat(state1).doesNotContain("/"); // URL-safe encoding
    }

    @Test
    void generateState_ShouldHaveConsistentLength() {
        // When
        String state = pkceService.generateState();

        // Then - 32 bytes encoded in base64 without padding = 43 characters
        assertThat(state).hasSize(43);
    }

    @Test
    void generateCodeChallenge_WithEmptyVerifier_ShouldProduceHash() {
        // When
        String challenge = pkceService.generateCodeChallenge("");

        // Then - empty string gets hashed without exception
        assertThat(challenge).isNotEmpty();
        assertThat(challenge).doesNotContain("=");
        assertThat(challenge).doesNotContain("+");
        assertThat(challenge).doesNotContain("/");
    }

    @Test
    void generateCodeChallenge_WithNullVerifier_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> pkceService.generateCodeChallenge(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate PKCE code challenge");
    }
}
