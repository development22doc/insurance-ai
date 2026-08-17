package com.claimassist.platform.agent_service.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeMetadataTest {

    @Test
    void hashIsDeterministicAndNonReversible() {
        assertThat(SafeMetadata.hash("CLM-99")).isEqualTo(SafeMetadata.hash("CLM-99"));
        assertThat(SafeMetadata.hash("CLM-99")).isNotEqualTo(SafeMetadata.hash("CLM-100"));
        assertThat(SafeMetadata.hash((Long) 99L)).isEqualTo(SafeMetadata.hash((Long) 99L));
        // 16 hex chars = 64 bits of a SHA-256 digest - never the raw value.
        assertThat(SafeMetadata.hash("CLM-99")).hasSize(16);
        assertThat(SafeMetadata.hash("CLM-99")).doesNotContain("CLM");
    }

    @Test
    void hashHandlesNullAndBlank() {
        assertThat(SafeMetadata.hash((String) null)).isEmpty();
        assertThat(SafeMetadata.hash((Long) null)).isEmpty();
        assertThat(SafeMetadata.hash("  ")).isEmpty();
    }

    @Test
    void redactRemovesBearerTokens() {
        String redacted = SafeMetadata.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature");
        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    void redactRemovesCredentialPairs() {
        String redacted = SafeMetadata.redact("apiKey=sk-12345secret password=supersecret");
        assertThat(redacted).doesNotContain("sk-12345secret").doesNotContain("supersecret");
    }

    @Test
    void redactIsSafeForNullAndOrdinaryText() {
        assertThat(SafeMetadata.redact(null)).isEmpty();
        assertThat(SafeMetadata.redact("What is the status of my claim?")).isEqualTo("What is the status of my claim?");
    }
}