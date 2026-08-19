package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionLoggingUtilTest {

    @Test
    void logException_emitsSanitizedMessageWithoutThrowing() {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-x");
        try {
            JoinPoint jp = mock(JoinPoint.class);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn("CustomerService.getCustomer(..)");
            when(jp.getSignature()).thenReturn(sig);

            ExceptionLoggingUtil.logException(jp,
                    new IllegalStateException("auth token=eyJhbGciOiJIUzI1NiJ9.secret"));

            when(jp.getSignature()).thenReturn(sig);
            ExceptionLoggingUtil.logException(jp, new IllegalArgumentException("boom"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void exceptionLoggingAspect_swallowsLoggingErrors() {
        ExceptionLoggingAspect aspect = new ExceptionLoggingAspect();
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getSignature()).thenThrow(new RuntimeException("mdc down"));

        aspect.logException(jp, new RuntimeException("npe"));
    }
}