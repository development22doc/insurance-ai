package com.claimassist.platform.common_lib.observability;

import org.springframework.core.env.Environment;

/**
 * Immutable developer identity resolved from the active Spring environment.
 * Request and reactive execution threads must populate their own MDC, so this
 * object deliberately carries the configured values instead of relying on
 * ApplicationReadyEvent's startup thread MDC.
 */
public final class DeveloperIdentity {
    private final String id;
    private final String name;

    public DeveloperIdentity(String id, String name) {
        this.id = nonBlankOrDefault(id, "local");
        this.name = nonBlankOrDefault(name, "unknown");
    }

    public static DeveloperIdentity from(Environment environment) {
        return new DeveloperIdentity(
                environment.getProperty("claimassist.dev.id"),
                environment.getProperty("claimassist.dev.name"));
    }

    public void populateMdc() {
        MDCUtility.putDeveloperId(id);
        MDCUtility.putDeveloperName(name);
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
