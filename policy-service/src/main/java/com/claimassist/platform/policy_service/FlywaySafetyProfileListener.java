package com.claimassist.platform.policy_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Runtime safety check to prevent Flyway from running against OCI databases.
 *
 * This listener enforces the read-only boundary for OCI PostgreSQL by:
 * 1. Detecting the local-k8s profile
 * 2. Checking if Flyway is accidentally enabled via environment variables
 * 3. Checking if the datasource points to OCI (Tailscale IP range)
 * 4. Failing fast if safety boundaries are violated
 */
public class FlywaySafetyProfileListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(FlywaySafetyProfileListener.class);

    private static final String LOCAL_K8S_PROFILE = "local-k8s";
    private static final String FLYWAY_ENABLED_PROPERTY = "spring.flyway.enabled";
    private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";
    private static final String OCI_TAILSCALE_IP = "100.114.133.69";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        String[] activeProfiles = environment.getActiveProfiles();
        boolean isLocalK8sProfile = false;

        for (String profile : activeProfiles) {
            if (LOCAL_K8S_PROFILE.equals(profile)) {
                isLocalK8sProfile = true;
                break;
            }
        }

        if (!isLocalK8sProfile) {
            // Safety check only applies to local-k8s profile
            return;
        }

        // Check if Flyway is enabled
        boolean flywayEnabled = Boolean.parseBoolean(environment.getProperty(FLYWAY_ENABLED_PROPERTY, "false"));

        if (flywayEnabled) {
            String datasourceUrl = environment.getProperty(DATASOURCE_URL_PROPERTY, "");

            if (datasourceUrl.contains(OCI_TAILSCALE_IP)) {
                // CRITICAL: Flyway is enabled and datasource points to OCI
                String errorMessage = String.format(
                    "SECURITY VIOLATION: Flyway is enabled (%s=true) in local-k8s profile with OCI datasource (%s). " +
                    "This would modify the read-only OCI PostgreSQL database. " +
                    "Action: Set FLYWAY_ENABLED=false or do not use local-k8s profile with Flyway enabled.",
                    FLYWAY_ENABLED_PROPERTY,
                    datasourceUrl
                );
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
        }

        log.info("Flyway safety check passed for local-k8s profile");
    }
}
