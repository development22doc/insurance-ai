package com.claimassist.platform.common_lib.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @Test
    void doFilter_usesIncomingCorrelationIdAndRequestId_setsMdcAndResponseHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "corr-123");
        request.addHeader(LoggingConstants.REQUEST_ID_HEADER, "req-9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedMdc = new String[1];
        FilterChain chain = (req, res) ->
                capturedMdc[0] = MDC.get(LoggingConstants.MDC_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, chain);

        assertThat(capturedMdc[0]).isEqualTo("corr-123");
        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo("corr-123");
        assertThat(response.getHeader(LoggingConstants.REQUEST_ID_HEADER)).isEqualTo("req-9");
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void doFilter_missingIds_generatesIdsAndStillSetsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedMdc = new String[1];
        FilterChain chain = (req, res) ->
                capturedMdc[0] = MDC.get(LoggingConstants.MDC_CORRELATION_ID);

        new CorrelationIdFilter().doFilter(request, response, chain);

        assertThat(capturedMdc[0]).isNotBlank();
        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
    }
}