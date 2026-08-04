package com.claimassist.platform.customer_service.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import com.claimassist.platform.common_lib.security.SecurityHeadersFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

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
    public SecurityFilterChain securityFilterChain (HttpSecurity httpSecurity) throws Exception {

        log.info ("Initializing Customer Service Security Filter Chain.");

        httpSecurity
                .csrf (csrf -> csrf
                        // Use a cookie-backed CSRF token repository for browser-based flows
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Ignore CSRF for actuator, webhook and API endpoints which are stateless/consumed by machines
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/actuator/**"),
                                new AntPathRequestMatcher("/webhooks/**"),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .cors (cors -> cors.configurationSource (corsConfigurationSource))
                .headers (headers -> {
                        headers.frameOptions (frameOptions -> frameOptions.deny ());
                        headers.xssProtection ();
                        headers.contentTypeOptions ();
                        headers.cacheControl ();
                        headers.httpStrictTransportSecurity (hsts -> hsts
                                .includeSubDomains (true)
                                .preload (true)
                                .maxAgeInSeconds (31536000));
                        headers.referrerPolicy (referrer -> referrer.policy (
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                        headers.permissionsPolicy (permissions -> permissions
                                .policy ("geolocation=(), microphone=(), camera=(), payment=()"));
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

                .oauth2ResourceServer (oauth2 -> oauth2
                        .jwt (jwt ->
                                jwt.jwtAuthenticationConverter (
                                        keycloakJwtAuthenticationConverter
                                )
                        )
                        .authenticationEntryPoint ((request, response, authException) ->
                                handlerExceptionResolver.resolveException (
                                        request,
                                        response,
                                        null,
                                        authException
                                )
                        )
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
        log.info ("JWT validation via JWKS enabled.");
        log.info ("OAuth2 Authorization Code + PKCE enabled.");

        return httpSecurity.build ();
    }

}