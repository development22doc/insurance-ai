package com.claimassist.platform.common_lib.observability;

/**
 * Helper utilities for safe logging: masking sensitive values, simple escaping.
 */
public final class LoggingHelper {
    private LoggingHelper() {}

    /**
     * Masks header or parameter values that are likely sensitive. Keeps the scheme
     * for Authorization headers (e.g. "Bearer ***").
     */
    public static String maskIfSensitive(String key, String value) {
        if (value == null) return "";
        String normalizedKey = key == null ? "" : key.toLowerCase();
        String trimmed = value == null ? "" : value.trim();
        if (normalizedKey.contains("authorization") || normalizedKey.contains("password")
                || normalizedKey.contains("secret") || normalizedKey.contains("client")
                || normalizedKey.contains("api") || normalizedKey.contains("token")
                || normalizedKey.contains("refresh") || normalizedKey.contains("access")) {
            if (normalizedKey.contains("authorization") && trimmed.contains(" ")) {
                String scheme = trimmed.substring(0, trimmed.indexOf(' '));
                return scheme + " ***";
            }
            return "***";
        }

        if (looksLikeJwt(trimmed) || looksLikeLongToken(trimmed)) {
            if (trimmed.toLowerCase().startsWith("bearer ")) {
                return "Bearer ***";
            }
            return "***";
        }

        return value;
    }

    private static boolean looksLikeJwt(String v) {
        return v != null && v.chars().filter(ch -> ch == '.').count() == 2;
    }

    private static boolean looksLikeLongToken(String v) {
        return v != null && v.length() > 40;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

