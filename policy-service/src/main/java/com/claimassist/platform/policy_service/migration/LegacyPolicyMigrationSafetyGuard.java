package com.claimassist.platform.policy_service.migration;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LegacyPolicyMigrationSafetyGuard {

    public boolean isApplyAllowed(Environment environment, String jdbcUrl) {
        if (environment == null) {
            return false;
        }

        String activeProfiles = String.join(",", environment.getActiveProfiles()).toLowerCase(Locale.ROOT);
        if (activeProfiles.contains("local-k8s") || activeProfiles.contains("prod") || activeProfiles.contains("production")) {
            return false;
        }

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return false;
        }

        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains("jdbc:tc:") || normalized.contains("jdbc:h2:mem:") || normalized.contains("jdbc:h2:file:")) {
            return true;
        }

        if (normalized.contains(".svc") || normalized.contains(".cluster.local") || normalized.contains("kubernetes") || normalized.contains("k8s")) {
            return false;
        }

        if (normalized.contains("prod") || normalized.contains("production") || normalized.contains("oci") || normalized.contains("oraclecloud")) {
            return false;
        }

        return normalized.contains("jdbc:postgresql://localhost")
                || normalized.contains("jdbc:postgresql://127.0.0.1")
                || normalized.contains("jdbc:postgresql://host.docker.internal")
                || normalized.contains("jdbc:postgresql://[::1]")
                || normalized.contains("jdbc:mysql://localhost")
                || normalized.contains("jdbc:mysql://127.0.0.1")
                || normalized.contains("jdbc:mysql://host.docker.internal");
    }
}
