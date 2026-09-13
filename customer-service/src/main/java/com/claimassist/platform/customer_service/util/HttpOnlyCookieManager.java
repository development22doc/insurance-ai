package com.claimassist.platform.customer_service.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manages secure HttpOnly cookies for authentication tokens.
 * Ensures tokens are never exposed to JavaScript.
 */
@Slf4j
public class HttpOnlyCookieManager {

    // Cookie names - consistent across frontend and backend
    public static final String ACCESS_TOKEN_COOKIE = "CLAIMASSIST_ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE = "CLAIMASSIST_REFRESH_TOKEN";

    // Cookie attributes for local development
    private static final String COOKIE_PATH = "/";
    private static final String COOKIE_DOMAIN = "localhost"; // Allow cross-port localhost access (5173, 8080, 8081, etc.)
    private static final String COOKIE_SAME_SITE = "Lax"; // Lax for LOCAL-K8S HTTP setup; strict enough for same-site requests
    private static final boolean COOKIE_SECURE = false; // false for localhost HTTP
    private static final int ACCESS_TOKEN_MAX_AGE = 3600; // 1 hour
    private static final int REFRESH_TOKEN_MAX_AGE = 86400 * 7; // 7 days

    /**
     * Sets the access token as an HttpOnly cookie.
     */
    public static void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie
                .from(ACCESS_TOKEN_COOKIE, accessToken)
                .domain(COOKIE_DOMAIN) // Explicit domain for localhost cross-port access
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(COOKIE_SECURE) // false for localhost HTTP
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(ACCESS_TOKEN_MAX_AGE)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Set access token HttpOnly cookie");
    }

    /**
     * Sets the refresh token as an HttpOnly cookie.
     */
    public static void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, refreshToken)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(COOKIE_SECURE) // false for localhost HTTP
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Set refresh token HttpOnly cookie");
    }

    /**
     * Sets both access and refresh tokens as HttpOnly cookies.
     */
    public static void setAuthTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        setAccessTokenCookie(response, accessToken);
        setRefreshTokenCookie(response, refreshToken);
    }

    /**
     * Clears the access token cookie.
     */
    public static void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(ACCESS_TOKEN_COOKIE, "")
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(COOKIE_SECURE)
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Cleared access token cookie");
    }

    /**
     * Clears the refresh token cookie.
     */
    public static void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, "")
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(COOKIE_SECURE)
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Cleared refresh token cookie");
    }

    /**
     * Clears both token cookies.
     */
    public static void clearAuthTokenCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }
}

