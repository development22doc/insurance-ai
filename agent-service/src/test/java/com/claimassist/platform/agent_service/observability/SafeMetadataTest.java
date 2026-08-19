package com.claimassist.platform.agent_service.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeMetadataTest {

    @Test
    void hashIsDeterministicAndShort() {
        String h1 = SafeMetadata.hash("customer-42");
        String h2 = SafeMetadata.hash("customer-42");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(16);
        assertThat(h1).matches("^[0-9a-f]{16}$");
    }

    @Test
    void hashReturnsEmptyForBlank() {
        assertThat(SafeMetadata.hash((String) null)).isEmpty();
        assertThat(SafeMetadata.hash("")).isEmpty();
        assertThat(SafeMetadata.hash("   ")).isEmpty();
    }

    @Test
    void hashLongHandlesNull() {
        assertThat(SafeMetadata.hash((Long) null)).isEmpty();
        assertThat(SafeMetadata.hash(123L)).isEqualTo(SafeMetadata.hash("123"));
    }

    @Test
    void redactBearerToken() {
        assertThat(SafeMetadata.redact("Authorization: Bearer abc.def-ghi_123"))
                .contains("bearer [REDACTED]")
                .doesNotContain("abc.def-ghi_123");
    }

    @Test
    void redactCredentialsWithColonOrEquals() {
        assertThat(SafeMetadata.redact("password=supersecret")).contains("[REDACTED]");
        assertThat(SafeMetadata.redact("api_key: 123456")).contains("[REDACTED]");
        assertThat(SafeMetadata.redact("secret = hunter2")).contains("[REDACTED]");
    }

    @Test
    void redactReturnsEmptyForNull() {
        assertThat(SafeMetadata.redact(null)).isEmpty();
    }

    @Test
    void redactLeavesNormalTextUntouched() {
        assertThat(SafeMetadata.redact("your claim is approved")).isEqualTo("your claim is approved");
    }
}