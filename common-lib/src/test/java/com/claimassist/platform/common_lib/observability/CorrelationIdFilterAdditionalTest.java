package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterAdditionalTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void echoTraceAndSpanHeadersWhenPresentInMdc() throws Exception {
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-abc");
        MDC.put(LoggingConstants.MDC_SPAN_ID, "span-xyz");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.TRACE_ID_HEADER)).isEqualTo("trace-abc");
        assertThat(response.getHeader(LoggingConstants.SPAN_ID_HEADER)).isEqualTo("span-xyz");
    }

    @Test
    void resolveTraceId_returnsMdcValue() {
        MDC.put(LoggingConstants.MDC_TRACE_ID, "mdc-trace");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        assertThat(filter.resolveTraceId(request)).isEqualTo("mdc-trace");
    }

    @Test
    void resolveTraceId_noContext_returnsNull() {
        MDC.clear();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        assertThat(filter.resolveTraceId(request)).isNull();
    }

    @Test
    void resolveSpanId_returnsMdcValue() {
        MDC.put(LoggingConstants.MDC_SPAN_ID, "mdc-span");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        assertThat(filter.resolveSpanId(request)).isEqualTo("mdc-span");
    }

    @Test
    void resolveSpanId_noContext_returnsNull() {
        MDC.clear();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        assertThat(filter.resolveSpanId(request)).isNull();
    }

    @Test
    void doFilter_buildsLogMaskingSensitiveQueryAndHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/auth/callback");
        request.setQueryString("code=abc123&state=xyz&nonSensitive=kept&access_token=TOPSECRET");
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("User-Agent", "curl/8.0");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def");
        request.addHeader("Cookie", "session=abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void doFilter_escapesControlCharactersInCorrelationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "safe\"inject\nnewline");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void doFilter_blankCorrelationHeaderGeneratesNew() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
    }
}