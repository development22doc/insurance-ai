package com.claimassist.platform.api_gateway.error;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the gateway translates downstream/edge failures into consistent, client-safe
 * HTTP statuses and messages that never leak stack traces, tokens or internal details.
 */
class GatewayExceptionHandlerTest {

    // A Mockito mock is used only to obtain a real instance of the class without invoking
    // its heavy constructor; the private methods under test are invoked for real via
    // reflection and do not depend on any instance state.
    private final GatewayExceptionHandler handler = mock(GatewayExceptionHandler.class);

    private HttpStatus resolveStatus(Throwable t) throws Exception {
        Method m = GatewayExceptionHandler.class.getDeclaredMethod("resolveStatus", Throwable.class);
        m.setAccessible(true);
        return (HttpStatus) m.invoke(handler, t);
    }

    private String resolveMessage(Throwable t, HttpStatus status) throws Exception {
        Method m = GatewayExceptionHandler.class.getDeclaredMethod("resolveMessage", Throwable.class, HttpStatus.class);
        m.setAccessible(true);
        return (String) m.invoke(handler, t, status);
    }

    @Test
    void notFoundMapsTo404WithRouteMessage() throws Exception {
        Throwable t = new ResponseStatusException(HttpStatus.NOT_FOUND);
        assertThat(resolveStatus(t)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resolveMessage(t, HttpStatus.NOT_FOUND)).isEqualTo("No route matches this request");
    }

    @Test
    void openCircuitBreakerMapsTo503() throws Exception {
        CallNotPermittedException t = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("api-gateway"));
        assertThat(resolveStatus(t)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resolveMessage(t, HttpStatus.SERVICE_UNAVAILABLE))
                .isEqualTo("Dependent service is temporarily unavailable - please retry shortly");
    }

    @Test
    void timeoutMapsTo504() throws Exception {
        Throwable t = new TimeoutException("boom");
        assertThat(resolveStatus(t)).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(resolveMessage(t, HttpStatus.GATEWAY_TIMEOUT)).isEqualTo("Dependent service call timed out");
    }

    @Test
    void connectionRefusedMapsTo502() throws Exception {
        Throwable t = new ConnectException("connection refused");
        assertThat(resolveStatus(t)).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resolveMessage(t, HttpStatus.BAD_GATEWAY)).isEqualTo("Unable to reach the dependent service");
    }

    @Test
    void ioFailureMapsTo502() throws Exception {
        assertThat(resolveStatus(new IOException("reset"))).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unexpectedErrorMapsTo500GenericMessage() throws Exception {
        Throwable t = new IllegalStateException("stack: secrets must not leak");
        assertThat(resolveStatus(t)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resolveMessage(t, HttpStatus.INTERNAL_SERVER_ERROR))
                .isEqualTo("An unexpected error occurred. Please try again later.");
    }

    @Test
    void responseStatusReasonIsPreserved() throws Exception {
        Throwable t = new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid claim payload");
        assertThat(resolveMessage(t, HttpStatus.BAD_REQUEST)).isEqualTo("invalid claim payload");
    }

    @Test
    void responseStatusWithoutReasonFallsBackToReasonPhrase() throws Exception {
        Throwable t = new ResponseStatusException(HttpStatus.CONFLICT);
        assertThat(resolveMessage(t, HttpStatus.CONFLICT)).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
    }
}