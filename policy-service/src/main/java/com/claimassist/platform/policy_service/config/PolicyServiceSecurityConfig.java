package com.claimassist.platform.policy_service.config;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.observability.DeveloperIdentity;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import com.claimassist.platform.policy_service.security.InternalRequestIdentity;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableMethodSecurity
@Slf4j
public class PolicyServiceSecurityConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter(Environment environment) {
        return new CorrelationIdFilter(DeveloperIdentity.from(environment));
    }

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    public CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    public InternalRequestIdentity internalRequestIdentity(CurrentUserProvider currentUserProvider) {
        return new InternalRequestIdentity(currentUserProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            CorrelationIdFilter correlationIdFilter,
            HandlerExceptionResolver handlerExceptionResolver,
            KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        log.info("Initializing Policy Service Security Filter Chain.");

        httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> {
                    headers.frameOptions(frameOptions -> frameOptions.deny());
                    headers.xssProtection();
                    headers.contentTypeOptions();
                    headers.cacheControl();
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .preload(true)
                            .maxAgeInSeconds(31536000));
                    headers.referrerPolicy(referrer -> referrer.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions
                            .policy("geolocation=(), microphone=(), camera=(), payment=()"));
                    headers.contentSecurityPolicy(csp -> csp
                            .policyDirectives("default-src 'self'; " +
                                    "script-src 'self'; " +
                                    "style-src 'self' 'unsafe-inline'; " +
                                    "img-src 'self' data: https:; " +
                                    "font-src 'self'; " +
                                    "connect-src 'self'; " +
                                    "frame-ancestors 'none'; " +
                                    "upgrade-insecure-requests; " +
                                    "block-all-mixed-content"));
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Allow minimal actuator health/info publicly; other actuator endpoints require authentication
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Stripe webhooks are signed and must be reachable without JWT — validate via signature inside controller
                        .requestMatchers("/api/v1/webhooks/stripe").permitAll()
                        // Swagger/OpenAPI should be authenticated in production; keep it behind auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) ->
                                handlerExceptionResolver.resolveException(request, response, null, authException))
                )
                .exceptionHandling(exception -> exception.accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                                handlerExceptionResolver.resolveException(request, response, null, accessDeniedException)
                ));

        return httpSecurity.build();
    }
}
