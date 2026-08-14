package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MDCUtilityTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void putCorrelationId_WithValidId_ShouldSetInMDC() {
        // When
        MDCUtility.putCorrelationId("test-correlation-id-123");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isEqualTo("test-correlation-id-123");
    }

    @Test
    void putCorrelationId_WithNullId_ShouldNotSetInMDC() {
        // When
        MDCUtility.putCorrelationId(null);

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void putCorrelationId_WithEmptyId_ShouldSetInMDC() {
        // When
        MDCUtility.putCorrelationId("");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isEqualTo("");
    }

    @Test
    void putRequestId_WithValidId_ShouldSetInMDC() {
        // When
        MDCUtility.putRequestId("test-request-id-456");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isEqualTo("test-request-id-456");
    }

    @Test
    void putRequestId_WithNullId_ShouldNotSetInMDC() {
        // When
        MDCUtility.putRequestId(null);

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void putRequestId_WithEmptyId_ShouldSetInMDC() {
        // When
        MDCUtility.putRequestId("");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isEqualTo("");
    }

    @Test
    void putTraceId_WithValidId_ShouldSetInMDC() {
        // When
        MDCUtility.putTraceId("test-trace-id");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isEqualTo("test-trace-id");
    }

    @Test
    void putTraceId_WithNullId_ShouldNotSetInMDC() {
        // When
        MDCUtility.putTraceId(null);

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
    }

    @Test
    void putSpanId_WithValidId_ShouldSetInMDC() {
        // When
        MDCUtility.putSpanId("test-span-id");

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isEqualTo("test-span-id");
    }

    @Test
    void putSpanId_WithNullId_ShouldNotSetInMDC() {
        // When
        MDCUtility.putSpanId(null);

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
    }

    @Test
    void clearAll_ShouldRemoveAllIdsFromMDC() {
        // Given
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "test-correlation-id");
        MDC.put(LoggingConstants.MDC_REQUEST_ID, "test-request-id");
        MDC.put(LoggingConstants.MDC_TRACE_ID, "test-trace-id");
        MDC.put(LoggingConstants.MDC_SPAN_ID, "test-span-id");

        // When
        MDCUtility.clearAll();

        // Then
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
    }
}
