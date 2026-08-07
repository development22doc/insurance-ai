package com.claimassist.platform.common_lib.observability;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small helpers for logging request-related details. Values are kept minimal to
 * avoid logging PII and secrets.
 */
public final class RequestLoggingUtil {
    private RequestLoggingUtil() {}

    public static String method(HttpServletRequest req) {
        return req == null ? "-" : (req.getMethod() == null ? "-" : req.getMethod());
    }

    public static String path(HttpServletRequest req) {
        return req == null ? "-" : (req.getRequestURI() == null ? "-" : req.getRequestURI());
    }

    public static String remoteIp(HttpServletRequest req) {
        if (req == null) return "-";
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}

