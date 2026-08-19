package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private boolean[] captureMdc() {
        return new boolean[1];
    }

    @Test
    void generatesAndEchoesCorrelationIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String correlation = response.getHeader(LoggingConstants.CORRELATION_ID_HEADER);
        assertThat(correlation).isNotBlank();
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull(); // cleaned up after chain
    }

    @Test
    void propagatesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "incoming-corr-99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo("incoming-corr-99");
    }

    @Test
    void correlationIsAvailableInsideChainAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "corr-inside");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] seenInside = captureMdc();

        filter.doFilter(request, response, (req, res) -> {
            seenInside[0] = "corr-inside".equals(MDC.get(LoggingConstants.MDC_CORRELATION_ID));
        });

        assertThat(seenInside[0]).isTrue();
        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void requestIdGeneratedAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void doesNotEchoAuthorizationTokenInResponseHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        request.addHeader("Authorization", "Bearer some.long.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Authorization")).isNull();
    }

    @Test
    void echoesTraceAndSpanIdsFromMdc() throws Exception {
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-abc");
        MDC.put(LoggingConstants.MDC_SPAN_ID, "span-xyz");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.TRACE_ID_HEADER)).isEqualTo("trace-abc");
        assertThat(response.getHeader(LoggingConstants.SPAN_ID_HEADER)).isEqualTo("span-xyz");
    }

    @Test
    void omitsTraceAndSpanHeadersWhenMdcHasNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.TRACE_ID_HEADER)).isNull();
        assertThat(response.getHeader(LoggingConstants.SPAN_ID_HEADER)).isNull();
    }

    @Test
    void blankCorrelationAndRequestHeadersAreTreatedAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "   ");
        request.addHeader(LoggingConstants.REQUEST_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
        assertThat(response.getHeader(LoggingConstants.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void mdcIsClearedEvenWhenDownstreamChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/claims/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new RuntimeException("chain failure");
        })).isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(LoggingConstants.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(LoggingConstants.MDC_REQUEST_ID)).isNull();
        assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void masksSensitiveQueryKeysViaReflection() throws Exception {
        String masked = ReflectionTestUtils.invokeMethod(filter, "maskQuery",
                "code=abc123&state=xyz&token=secret&client_secret=s3cret&normal=5");
        assertThat(masked)
                .contains("code=***")
                .contains("state=xyz")
                .contains("token=***")
                .contains("client_secret=***")
                .contains("normal=5")
                .doesNotContain("abc123")
                .doesNotContain("s3cret");
    }

    @Test
    void maskingNullOrEmptyQueryYieldsEmpty() throws Exception {
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskQuery", (Object) null)).isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskQuery", "")).isEmpty();
    }

    @Test
    void masksSensitiveHeaderValuesAndPreservesScheme() throws Exception {
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive",
                "Authorization", "Bearer some.token.value")).isEqualTo("Bearer ***");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive",
                "Password", "hunter2")).isEqualTo("***");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive",
                "Cookie", "sid=abc")).isEqualTo("sid=abc");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive",
                "X-Token", "veryLongStringThatLooksLikeAToken101010101010101010")).isEqualTo("***");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive",
                "User-Agent", null)).isEmpty();
    }

    @Test
    void maskIfSensitiveMasksJwtLookingValues() throws Exception {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "maskIfSensitive", "X-Custom", jwt))
                .isEqualTo("***");
    }

    @Test
    void escapeNeutralizesControlCharactersAndQuotes() throws Exception {
        Object escaped = ReflectionTestUtils.invokeMethod(filter, "escape", "a\"b\\c\nd\re\tf\u0007");
        assertThat(escaped).isEqualTo("a\\\"b\\\\c\\nd\\re\\tf\\u0007");
    }

    @Test
    void valueOrDashHandlesNullBlankAndValue() throws Exception {
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "valueOrDash", (Object) null)).isEqualTo("-");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "valueOrDash", "  ")).isEqualTo("-");
        assertThat((String) ReflectionTestUtils.invokeMethod(filter, "valueOrDash", "v")).isEqualTo("v");
    }
}