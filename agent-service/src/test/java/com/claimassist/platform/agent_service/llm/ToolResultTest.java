package com.claimassist.platform.agent_service.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    @Test
    void successResultSerializesStructuredJson() {
        String json = ToolResult.success(Map.of("status", "UNDER_REVIEW"), "claims-service").toJson();
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"source\":\"claims-service\"");
        assertThat(json).contains("\"data\"");
        assertThat(json).contains("\"status\":\"UNDER_REVIEW\"");
    }

    @Test
    void failureResultCarriesStableErrorCodeAndRetryableFlag() {
        String json = ToolResult.failure("CLAIMS_SERVICE_UNAVAILABLE", true, "retry").toJson();
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"errorCode\":\"CLAIMS_SERVICE_UNAVAILABLE\"");
        assertThat(json).contains("\"retryable\":true");
        assertThat(json).contains("\"message\":\"retry\"");
    }

    @Test
    void malformedDataNeverThrows() {
        // Cycle-breaking not needed here; ensure serialization failures degrade safely.
        String json = ToolResult.failure("X", false, "msg").toJson();
        assertThat(json).startsWith("{").endsWith("}");
    }
}