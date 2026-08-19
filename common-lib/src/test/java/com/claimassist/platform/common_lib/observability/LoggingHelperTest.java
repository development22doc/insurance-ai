package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingHelperTest {

    @Test
    void maskIfSensitive_nullValue_returnsEmpty() {
        assertThat(LoggingHelper.maskIfSensitive("Authorization", null)).isEmpty();
        assertThat(LoggingHelper.maskIfSensitive(null, null)).isEmpty();
    }

    @Test
    void maskIfSensitive_authorizationPreservesScheme() {
        assertThat(LoggingHelper.maskIfSensitive("Authorization", "Bearer abc")).isEqualTo("Bearer ***");
    }

    @Test
    void maskIfSensitive_sensitiveKeys_masked() {
        assertThat(LoggingHelper.maskIfSensitive("Password", "x")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("secret-key", "x")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("client-id", "x")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("X-Api-Key", "x")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("access-token", "x")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("refresh-token", "x")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_nonSensitiveKey_returnsOriginal() {
        assertThat(LoggingHelper.maskIfSensitive("X-Custom", "hello")).isEqualTo("hello");
    }

    @Test
    void maskIfSensitive_jwtAndLongTokens_masked() {
        assertThat(LoggingHelper.maskIfSensitive("X", "eyJh.eyJi.eyJj")).isEqualTo("***");
        assertThat(LoggingHelper.maskIfSensitive("X", "Bearer eyJh.eyJi.eyJj")).isEqualTo("Bearer ***");
        assertThat(LoggingHelper.maskIfSensitive("X", "Averyverylongstringthatexceedsfortycharacters1234567890"))
                .isEqualTo("***");
    }

    @Test
    void escapeJson_escapesQuotesAndBackslashes() {
        assertThat(LoggingHelper.escapeJson("a\"b\\c")).isEqualTo("a\\\"b\\\\c");
    }

    @Test
    void escapeJson_null_returnsEmpty() {
        assertThat(LoggingHelper.escapeJson(null)).isEmpty();
    }
}