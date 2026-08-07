package com.claimassist.platform.common_lib.observability;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Small helpers for response-related logging.
 */
public final class ResponseLoggingUtil {
    private ResponseLoggingUtil() {}

    public static int status(HttpServletResponse resp) {
        return resp == null ? 0 : resp.getStatus();
    }
}

