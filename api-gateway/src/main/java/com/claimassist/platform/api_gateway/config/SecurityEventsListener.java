package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.common_lib.observability.LoggingHelper;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.access.event.AuthorizationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Listens to Spring Security application events and emits structured SECURITY
 * events via the shared EventLogger. Keeps a minimal amount of information
 * (no tokens, no PII) and uses LoggingHelper to escape/mask sensitive values.
 */
@Component
public class SecurityEventsListener {

    private final Optional<EventLogger> eventLogger;

    public SecurityEventsListener(Optional<EventLogger> eventLogger) {
        this.eventLogger = eventLogger;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent ev) {
        Authentication auth = ev.getAuthentication();
        String principal = auth.getName();
        Map<String, Object> details = Map.of(
                "event", "authenticationSuccess",
                "username", principal,
                "authorities", auth.getAuthorities().toString()
        );
        eventLogger.ifPresent(el -> el.logSecurityEvent("api-gateway", "api-gateway", details));
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent ev) {
        Throwable ex = ev.getException();
        String eventType = "authenticationFailure";
        if (ex instanceof JwtException) {
            String m = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (m.contains("expired")) eventType = "jwtExpired";
            else eventType = "jwtInvalid";
        }

        Map<String, Object> details = Map.of(
                "event", eventType,
                "exceptionType", ex.getClass().getSimpleName(),
                "message", LoggingHelper.escapeJson(ex.getMessage())
        );

        eventLogger.ifPresent(el -> el.logSecurityEvent("api-gateway", "api-gateway", details));
    }

    @EventListener
    public void onAuthorizationFailure(AuthorizationFailureEvent ev) {
        // AuthorizationFailureEvent contains details about the authorization attempt
        Map<String, Object> details = Map.of(
                "event", "accessDenied",
                "detail", ev.toString()
        );
        eventLogger.ifPresent(el -> el.logSecurityEvent("api-gateway", "api-gateway", details));
    }
}

