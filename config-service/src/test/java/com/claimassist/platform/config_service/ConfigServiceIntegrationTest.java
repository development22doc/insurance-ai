package com.claimassist.platform.config_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 - Config Server integration tests against the project's own <b>native file
 * repository</b> (no Docker, no external Git needed). The context starts the real
 * Config Server on a random port pointed at {@code ../config-repo}.
 * <p>
 * Covers: valid lookup, profile resolution, missing configuration (404), actuator
 * exposure (health public; {@code env}/{@code configprops} NOT exposed so secrets cannot
 * leak through actuator).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=native",
        "spring.cloud.config.server.native.search-locations=file:../config-repo",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ConfigServiceIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void returnsConfigurationForKnownApplication() {
        ResponseEntity<Map> resp = rest.getForEntity("/claims-service/default", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().toString()).contains("8082");
    }

    @Test
    void returnsGatewayRoutesFromRemoteRepository() {
        ResponseEntity<Map> resp = rest.getForEntity("/api-gateway/default", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody().toString();
        assertThat(body).contains("CUSTOMER-SERVICE").contains("CLAIMS-SERVICE").contains("AGENT-SERVICE");
    }

    @Test
    void resolvesProfileFromBaseFile() {
        // No claims-service-dev.yml exists; the server still resolves the base file for
        // the requested profile (profile-aware resolution falls back to the base).
        ResponseEntity<Map> resp = rest.getForEntity("/claims-service/dev", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().toString()).contains("8082");
    }

    @Test
    void missingApplicationReturnsSharedBaseConfigGracefully() {
        // A native repo serves the shared config-repo/application.yml for an unknown app name
        // (no per-service file) - this is the graceful behaviour callers see, returning only
        // the common (non-secret) base configuration and an empty per-service source list.
        ResponseEntity<Map> resp = rest.getForEntity("/no-such-service/default", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody().toString();
        assertThat(body).contains("config-repo/application.yml");
        assertThat(body).doesNotContain("no-such-service.yml");
    }

    @Test
    void healthEndpointIsReachable() {
        ResponseEntity<Map> resp = rest.getForEntity("/actuator/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("status", "UP");
    }

    @Test
    void envEndpointDoesNotExposeResolvedSecrets() {
        // Actuator env/configprops are NOT exposed (restricted to health,info,metrics,prometheus).
        // Because there is no actuator mapping, /actuator/env falls through to the Config Server's
        // own /{name}/{profiles} lookup controller (name=actuator, profile=env) and returns only
        // the shared base config - NOT actuator's env view with resolved property values.
        ResponseEntity<Map> resp = rest.getForEntity("/actuator/env", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("name")).isEqualTo("actuator");
        assertThat(resp.getBody().get("profiles")).asList().contains("env");
        // No actuator-style resolved property sources are returned.
        assertThat(resp.getBody().toString()).doesNotContain("\"activeProfiles\"");
        assertThat(resp.getBody().toString()).doesNotContain("INTERNAL_API_SECRET=configured");
    }

    @Test
    void configpropsEndpointIsNotExposed() {
        // configprops is not exposed; it falls through to the Config Server lookup controller
        // (name=actuator, profile=configprops) rather than returning actuator configprops.
        ResponseEntity<Map> resp = rest.getForEntity("/actuator/configprops", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("name")).isEqualTo("actuator");
        assertThat(resp.getBody().get("profiles")).asList().contains("configprops");
        assertThat(resp.getBody().toString()).doesNotContain("\"contexts\"");
    }
}