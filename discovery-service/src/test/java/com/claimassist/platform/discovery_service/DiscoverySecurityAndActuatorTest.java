package com.claimassist.platform.discovery_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 - Discovery Server security & actuator behaviour, exercised against the real
 * embedded server (RANDOM_PORT + TestRestTemplate) so the Eureka Jersey REST endpoints are
 * actually served.
 * <p>
 * Eureka client registration / fetch / dashboard and liveness probes must remain reachable
 * (that is how services register, heartbeat and are routed to), while actuator must NOT
 * expose {@code env}/{@code configprops} (which could leak registry/secrets), and anything
 * that is not explicitly permitted must require authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka",
        "eureka.server.enable-self-preservation=false",
        "eureka.server.wait-time-in-ms-when-sync-empty=0"
})
class DiscoverySecurityAndActuatorTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registryEndpointIsReachable() {
        ResponseEntity<String> resp = rest.getForEntity("/eureka/apps", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("applications");
    }

    @Test
    void dashboardIsReachable() {
        ResponseEntity<String> resp = rest.getForEntity("/", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthEndpointIsReachable() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("UP");
    }

    @Test
    void envEndpointIsNotExposed() {
        // Restricted actuator exposure: env/configprops must not return data. Under the real
        // embedded Eureka server an unexposed actuator endpoint is denied (403) rather than
        // mapped (404) - either way it is NOT reachable, so no secrets can be read back.
        ResponseEntity<String> resp = rest.getForEntity("/actuator/env", String.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void configpropsEndpointIsNotExposed() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/configprops", String.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void nonPermittedPathRequiresAuthentication() {
        // Only /, /eureka/**, /actuator/** are permitted; anything else requires auth.
        ResponseEntity<String> resp = rest.getForEntity("/something/unexpected", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}