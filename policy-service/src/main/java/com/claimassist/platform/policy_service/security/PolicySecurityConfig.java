package com.claimassist.platform.policy_service.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@Slf4j
public class PolicySecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        log.info("Initializing Policy Service Security Filter Chain.");

        httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> {
                    headers.frameOptions(frameOptions -> frameOptions.deny());
                    headers.xssProtection();
                    headers.contentTypeOptions();
                    headers.cacheControl();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/policies/products/**").permitAll()
                        .requestMatchers("/api/v1/policies/plans/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        correlationIdFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) ->
                                                            handlerExceptionResolver.resolveException(request, response, null, authException)
                        )
                )
                .exceptionHandling(exception -> exception.accessDeniedHandler((request, response, accessDeniedException) ->
                        handlerExceptionResolver.resolveException(request, response, null, accessDeniedException)
                ));

        return httpSecurity.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addAllowedOriginPattern("*");
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
