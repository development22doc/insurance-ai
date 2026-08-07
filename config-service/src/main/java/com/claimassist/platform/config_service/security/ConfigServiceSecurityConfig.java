package com.claimassist.platform.config_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security config for the Config Server used in local/dev environments.
 * Allows unauthenticated GET access to the Config Server REST endpoints so
 * Spring Cloud Config clients (spring.config.import / bootstrap) can fetch
 * configuration without being redirected to a login HTML page.
 */
@Configuration
public class ConfigServiceSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                // Allow config clients to GET configuration without authentication
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                // Allow actuator endpoints for health checks
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            // Do not use form login or http basic for services
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}

