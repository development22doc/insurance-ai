package com.claimassist.platform.customer_service.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@Slf4j
public class CustomerSecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public Filter inboundAuthorizationDiagnosticFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String path = httpRequest.getRequestURI();
                String authHeader = httpRequest.getHeader("Authorization");
                boolean authorizationHeaderReceived = authHeader != null && authHeader.startsWith("Bearer ");
                log.info("=== CUSTOMER PRE-BEARER === path={} authorizationHeaderReceived={}", path, authorizationHeaderReceived);

                chain.doFilter(request, response);

                // Check authentication status AFTER the filter chain (after BearerTokenAuthenticationFilter)
                // Log for all internal endpoints, not just /customers/me
                if (path.startsWith("/internal/v1/")) {
                    try {
                        org.springframework.security.core.Authentication auth =
                            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if (auth != null) {
                            log.info("=== CUSTOMER POST-BEARER === path={} auth_class={} is_authenticated={}", path, auth.getClass().getSimpleName(), auth.isAuthenticated());
                            if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                                log.info("=== CUSTOMER JWT CLAIMS === path={} sub_present={} preferred_username_present={} userId_present={} email_present={}",
                                    path,
                                    jwt.getClaimAsString("sub") != null,
                                    jwt.getClaimAsString("preferred_username") != null,
                                    jwt.getClaim("userId") != null,
                                    jwt.getClaimAsString("email") != null);
                            } else {
                                log.info("=== CUSTOMER POST-BEARER === path={} Principal is not JWT: {}", path, auth.getPrincipal().getClass().getSimpleName());
                            }
                        } else {
                            log.info("=== CUSTOMER POST-BEARER === path={} No authentication in SecurityContext", path);
                        }
                    } catch (Exception e) {
                        log.info("=== CUSTOMER POST-BEARER === path={} Error reading SecurityContext: {}", path, e.getMessage());
                    }
                }
            }

            @Override
            public void init(FilterConfig filterConfig) throws ServletException {}

            @Override
            public void destroy() {}
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain (HttpSecurity httpSecurity) throws Exception {

        log.info ("Initializing Customer Service Security Filter Chain.");

        httpSecurity
                // Stateless JWT-bearer resource server behind the API gateway: this
                // service has no cookie/session-based authentication and is not
                // browser-reachable externally, so CSRF (which relies on ambient
                // browser credentials) provides no protection here. Consistent with
                // claims-service and the gateway, CSRF is disabled.
                .csrf (AbstractHttpConfigurer :: disable)
                .cors (cors -> cors.configurationSource (corsConfigurationSource))
                .headers (headers -> {
                        headers.frameOptions (frameOptions -> frameOptions.deny ());
                        headers.xssProtection(Customizer.withDefaults());
                        headers.contentTypeOptions(Customizer.withDefaults());
                        headers.cacheControl(Customizer.withDefaults());
                        headers.httpStrictTransportSecurity (hsts -> hsts
                                .includeSubDomains (true)
                                .preload (true)
                                .maxAgeInSeconds (31536000));
                        headers.referrerPolicy (referrer -> referrer.policy (
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                        headers.permissionsPolicyHeader(permissions -> permissions
                        .policy("geolocation=(), microphone=(), camera=(), payment=()"));
                        headers.contentSecurityPolicy (csp -> csp
                                .policyDirectives ("default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "upgrade-insecure-requests; " +
                                        "block-all-mixed-content"));
                })
                .sessionManagement (session ->
                        session.sessionCreationPolicy (SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests (auth -> auth

                        .dispatcherTypeMatchers (DispatcherType.ASYNC).permitAll ()
                        .dispatcherTypeMatchers (DispatcherType.ERROR).permitAll ()

                        .requestMatchers (
                                "/auth/signup",
                                "/auth/authorize",
                                "/auth/callback",
                                "/auth/refresh",
                                "/auth/logout",
                                "/webhooks/**",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll ()

                        .anyRequest ().authenticated ()

                )

                .addFilterBefore (
                        correlationIdFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore (
                        inboundAuthorizationDiagnosticFilter(),
                        UsernamePasswordAuthenticationFilter.class
                )
                // CustomerCookieAuthenticationFilter removed - Gateway now sends Authorization header
                // directly, so cookie-to-header conversion is not needed

                .oauth2ResourceServer (oauth2 -> oauth2
                        .jwt (jwt ->
                                jwt.jwtAuthenticationConverter (
                                        keycloakJwtAuthenticationConverter
                                )
                        )
                        // Use a terminal AuthenticationEntryPoint that writes a minimal
                        // safe JSON 401 response and does NOT re-enter the MVC
                        // HandlerExceptionResolver. This prevents the request from
                        // continuing to controller code after bearer validation fails.
                        .authenticationEntryPoint((request, response, authException) -> {
                            try {
                                response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
                                response.setContentType("application/json;charset=UTF-8");
                                // Minimal, non-sensitive payload
                                String body = "{\"error\":\"Unauthorized\",\"message\":\"Authentication failed\"}";
                                response.getWriter().write(body);
                                response.getWriter().flush();
                            } catch (java.io.IOException e) {
                                // If writing fails, fall back to setting status only
                                response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
                            }
                        })
                )

                .exceptionHandling (exception ->

                        exception.accessDeniedHandler (
                                (request, response, accessDeniedException) ->
                                        handlerExceptionResolver.resolveException (
                                                request,
                                                response,
                                                null,
                                                accessDeniedException
                                        )
                        )
                );

        log.info ("Customer Security Filter Chain initialized successfully.");
        log.info ("InternalApiKeyFilter removed.");
        log.info ("CustomerCookieAuthenticationFilter removed - Gateway sends Authorization header directly.");
        log.info ("JWT validation via JWKS enabled.");
        log.info ("OAuth2 Authorization Code + PKCE enabled.");

        return httpSecurity.build ();
    }

}
