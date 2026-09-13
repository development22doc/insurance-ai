package com.claimassist.platform.customer_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that supports authentication via HttpOnly cookies.
 * Extracts the access token from the HttpOnly cookie and adds it as an Authorization header.
 * This allows the standard OAuth2 Resource Server filter to handle JWT validation.
 *
 * DEPRECATED: This filter is no longer used. The API Gateway now sends the Authorization header
 * directly to downstream services. This class is kept for reference only and should be removed
 * after verifying the Gateway authentication path is working correctly.
 */
@Slf4j
// @Component - REMOVED to prevent automatic registration
public class CustomerCookieAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "CLAIMASSIST_ACCESS_TOKEN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info("=== CustomerCookieAuthenticationFilter START === path={}", path);

        // Check if Authorization header is present from Gateway
        String authHeader = request.getHeader("Authorization");
        boolean authorizationHeaderPresent = authHeader != null && authHeader.startsWith("Bearer ");
        log.info("authorizationHeaderPresent={}", authorizationHeaderPresent);

        if (authorizationHeaderPresent) {
            log.info("authorizationHeaderReceived=true - Header present from Gateway, skipping cookie processing");
            chain.doFilter(request, response);
            return;
        }
        log.info("authorizationHeaderReceived=false - No Authorization header from Gateway");

        // Try to extract token from HttpOnly cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                    String token = cookie.getValue();
                    log.info("authorizationHeaderCreated=true - Adding Authorization header from cookie");

                    // Wrap the request to add the Authorization header
                    final String bearerToken = "Bearer " + token;
                    jakarta.servlet.http.HttpServletRequestWrapper wrappedRequest = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return bearerToken;
                            }
                            return super.getHeader(name);
                        }

                        @Override
                        public java.util.Enumeration<String> getHeaders(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return java.util.Collections.enumeration(java.util.Collections.singletonList(bearerToken));
                            }
                            return super.getHeaders(name);
                        }
                    };

                    chain.doFilter(wrappedRequest, response);
                    return;
                }
            }
        }

        log.info("authorizationHeaderCreated=false - No access cookie");
        // If no cookie, proceed with normal authentication flow
        chain.doFilter(request, response);
    }
}
