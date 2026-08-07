package com.claimassist.platform.common_lib.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for performance logging thresholds. Allows a global
 * default and optional per-category overrides. Values are in milliseconds.
 */
@ConfigurationProperties(prefix = "commonlib.performance")
public class PerformanceLoggingProperties {
    /** Global default thresholds applied when category-specific values aren't set. */
    private long infoThresholdMs = 200;
    private long warnThresholdMs = 1000;
    private long errorThresholdMs = 5000;

    /** Optional per-category thresholds: key is category name (e.g. "API", "DATABASE"). */
    private Map<String, Thresholds> categories = new HashMap<>();

    public static class Thresholds {
        private long info = 200;
        private long warn = 1000;
        private long error = 5000;

        public long getInfo() { return info; }
        public void setInfo(long info) { this.info = info; }
        public long getWarn() { return warn; }
        public void setWarn(long warn) { this.warn = warn; }
        public long getError() { return error; }
        public void setError(long error) { this.error = error; }
    }

    public long getInfoThresholdMs() { return infoThresholdMs; }
    public void setInfoThresholdMs(long infoThresholdMs) { this.infoThresholdMs = infoThresholdMs; }
    public long getWarnThresholdMs() { return warnThresholdMs; }
    public void setWarnThresholdMs(long warnThresholdMs) { this.warnThresholdMs = warnThresholdMs; }
    public long getErrorThresholdMs() { return errorThresholdMs; }
    public void setErrorThresholdMs(long errorThresholdMs) { this.errorThresholdMs = errorThresholdMs; }
    public Map<String, Thresholds> getCategories() { return categories; }
    public void setCategories(Map<String, Thresholds> categories) { this.categories = categories; }
}

