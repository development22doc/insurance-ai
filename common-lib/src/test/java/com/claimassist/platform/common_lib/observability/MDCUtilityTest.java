package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MDCUtilityTest {

    @Test
    void putsAndClearsAllKeys() {
        MDCUtility.putCorrelationId("corr");
        MDCUtility.putTraceId("trace");
        MDCUtility.putSpanId("span");
        MDCUtility.putRequestId("req");

        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isEqualTo("corr");
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID)).isEqualTo("trace");
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID)).isEqualTo("span");
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_REQUEST_ID)).isEqualTo("req");

        MDCUtility.clearAll();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void ignoresNullValues() {
        MDCUtility.putCorrelationId(null);
        MDCUtility.putTraceId(null);
        MDCUtility.putSpanId(null);
        MDCUtility.putRequestId(null);
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
        assertThat(org.slf4j.MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
    }
}