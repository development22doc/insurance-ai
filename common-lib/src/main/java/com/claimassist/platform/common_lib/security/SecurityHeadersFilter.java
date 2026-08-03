package com.claimassist.platform.common_lib.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to add comprehensive security headers to all HTTP responses.
 * These headers provide defense-in-depth against common web vulnerabilities.
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse httpResponse) {
            // Enforce HTTPS for all connections (18000 seconds = 180 days, with preload)
            httpResponse.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");

            // Prevent MIME type sniffing
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // Prevent clickjacking attacks - deny framing from external sites
            httpResponse.setHeader("X-Frame-Options", "DENY");

            // Control referrer information leakage
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Prevent XSS attacks (legacy, modern browsers use CSP instead)
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

            // Disable unnecessary browser features
            httpResponse.setHeader("Permissions-Policy",
                    "geolocation=(), microphone=(), camera=(), payment=()");

            // Content Security Policy - restrictive by default
            // Adjust directive URLs based on your actual resource origins
            httpResponse.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "upgrade-insecure-requests; " +
                    "block-all-mixed-content");
        }

        chain.doFilter(request, response);
    }
}

