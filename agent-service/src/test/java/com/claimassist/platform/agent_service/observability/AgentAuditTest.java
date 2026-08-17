package com.claimassist.platform.agent_service.observability;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Audit event design and emission (Phase 6.15 / 6.17). Audit events carry the
 * canonical correlation + result fields and are routed to the security or
 * business observability channel depending on whether they are security
 * significant. They never carry secrets or raw payloads.
 */
class AgentAuditTest {

    @Test
    void auditEventCarriesCanonicalCorrelationFields() {
        AuditEvent event = AuditEvent.of("CLAIM_UPDATE_PROPOSED", 7L, 99L, "req-9", "corr-9",
                "propose_claim_update", "ACCEPTED", "DOCS_REQUESTED");

        assertThat(event.auditEventId()).isNotBlank();
        assertThat(event.eventType()).isEqualTo("CLAIM_UPDATE_PROPOSED");
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.conversationId()).isEqualTo(99L);
        assertThat(event.requestId()).isEqualTo("req-9");
        assertThat(event.correlationId()).isEqualTo("corr-9");
        assertThat(event.tool()).isEqualTo("propose_claim_update");
        assertThat(event.result()).isEqualTo("ACCEPTED");
        assertThat(event.reason()).isEqualTo("DOCS_REQUESTED");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void businessAuditIsEmittedThroughBusinessChannel() {
        EventLogger logger = mock(EventLogger.class);
        new AgentTelemetry(logger, null, null, null)
                .audit(AuditEvent.of("CLAIM_UPDATE_PROPOSED", 7L, 99L, "r", "c", "propose_claim_update",
                        "ACCEPTED", "UNDER_REVIEW"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(logger).logBusinessEvent(isNull(), isNull(), captor.capture());
        Map<String, Object> d = captor.getValue();
        assertThat(d.get("eventType")).isEqualTo("CLAIM_UPDATE_PROPOSED");
        assertThat(d.get("tool")).isEqualTo("propose_claim_update");
        assertThat(d.get("result")).isEqualTo("ACCEPTED");
        // No secrets/payloads present in an audit event.
        assertThat(d).doesNotContainKey("note").doesNotContainKey("payload");
    }
}