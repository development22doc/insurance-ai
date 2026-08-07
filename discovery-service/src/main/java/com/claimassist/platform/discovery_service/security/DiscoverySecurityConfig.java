package com.claimassist.platform.discovery_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security config for the Eureka server.
 * Ensures Eureka REST endpoints are accessible without authentication so
 * discovery clients (register/heartbeat/fetch) can operate.
 */
@Configuration
public class DiscoverySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                // Allow Eureka client registration and discovery operations
                .requestMatchers("/eureka/**").permitAll()
                // Allow actuator endpoints used by health checks
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}

