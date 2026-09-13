package com.claimassist.platform.customer_service.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Attempts to resolve the Keycloak admin client secret from the authoritative
 * Kubernetes Secret when the in-process environment does not already provide
 * KEYCLOAK_ADMIN_CLIENT_SECRET. This allows developers to start services from
 * IntelliJ without manually exporting a secret while keeping the cluster
 * secret as the single source of truth.
 *
 * Important constraints:
 * - Never prints the secret to stdout/stderr.
 * - Behaves silently if kubectl is not available or the secret cannot be read.
 */
public class KeycloakSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SECRET_ENV = "KEYCLOAK_ADMIN_CLIENT_SECRET";
    private static final String NAMESPACE = "claimassist-dev";
    private static final String SECRET_NAME = "claimassist-keycloak-admin";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            String existing = environment.getProperty(SECRET_ENV);
            if (existing != null && !existing.isBlank()) {
                return; // already configured by env or properties
            }

            // Try to execute kubectl to read the secret value from the cluster
            ProcessBuilder pb = new ProcessBuilder(
                    "kubectl",
                    "-n", NAMESPACE,
                    "get", "secret", SECRET_NAME,
                    "-o", "jsonpath={.data.KEYCLOAK_ADMIN_CLIENT_SECRET}"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (InputStream is = p.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                is.transferTo(baos);
                int exit = p.waitFor();
                if (exit != 0) {
                    // kubectl returned non-zero; silently skip
                    return;
                }

                String b64 = baos.toString(StandardCharsets.UTF_8).trim();
                if (b64.isEmpty()) return;

                // decode base64, do not log the value
                byte[] decoded = Base64.getDecoder().decode(b64);
                String secret = new String(decoded, StandardCharsets.UTF_8);

                Map<String, Object> props = new HashMap<>();
                props.put(SECRET_ENV, secret);

                // Insert high-priority property source so Spring binds it into @ConfigurationProperties
                environment.getPropertySources().addFirst(new MapPropertySource("keycloak-secret-kubectl", props));

                // Record that we injected the secret (non-sensitive marker). This helps runtime verification
                // without ever logging or printing the secret itself.
                try {
                    System.setProperty("claimassist.keycloak.secret.injection", "kubectl");
                } catch (Exception ignore) {
                    // ignore
                }
            }
        } catch (Exception ignored) {
            // Intentionally swallow exceptions to avoid startup failures when kubectl is absent
        }
    }

    @Override
    public int getOrder() {
        // run early
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

