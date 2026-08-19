package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

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

    // ---- maskIfSensitive tests (package-private method) ----

    @Test
    void maskIfSensitive_nullValueReturnsEmpty() {
        assertThat(filter.maskIfSensitive("Authorization", null)).isEqualTo("");
    }

    @Test
    void maskIfSensitive_authorizationBearerMasked() {
        assertThat(filter.maskIfSensitive("Authorization", "Bearer token123")).isEqualTo("Bearer ***");
    }

    @Test
    void maskIfSensitive_passwordMasked() {
        assertThat(filter.maskIfSensitive("Password", "mypassword")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_secretMasked() {
        assertThat(filter.maskIfSensitive("Secret", "mysecret")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_clientMasked() {
        assertThat(filter.maskIfSensitive("Client", "myclient")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_apiMasked() {
        assertThat(filter.maskIfSensitive("X-Api-Key", "myapikey")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_tokenMasked() {
        assertThat(filter.maskIfSensitive("Token", "mytoken")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_refreshMasked() {
        assertThat(filter.maskIfSensitive("Refresh", "refreshvalue")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_accessMasked() {
        assertThat(filter.maskIfSensitive("Access", "accessvalue")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_nonSensitiveKeyReturnsValue() {
        assertThat(filter.maskIfSensitive("X-Custom", "normalvalue")).isEqualTo("normalvalue");
    }

    @Test
    void maskIfSensitive_jwtLikeValueMasked() {
        assertThat(filter.maskIfSensitive("X-Custom", "eyJhbGciOiJIUzI1NiJ9.abc.def")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_longTokenLikeValueMasked() {
        assertThat(filter.maskIfSensitive("X-Custom", "Averyverylongbas64tokenstringthatexceedsfortycharacters")).isEqualTo("***");
    }

    @Test
    void maskIfSensitive_authorizationWithSpacePreservesScheme() {
        assertThat(filter.maskIfSensitive("Authorization", "Bearer mytoken123")).isEqualTo("Bearer ***");
    }

    @Test
    void maskIfSensitive_whitespaceValueIsMasked() {
        assertThat(filter.maskIfSensitive("Authorization", "   ")).isEqualTo("***");
    }
}