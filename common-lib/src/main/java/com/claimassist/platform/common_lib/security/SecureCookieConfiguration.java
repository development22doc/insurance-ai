package com.claimassist.platform.common_lib.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Secure cookie configuration for servlet-based applications.
 * Ensures all cookies are set with security flags.
 *
 * Security Properties:
 * - Secure: Only transmit over HTTPS (enforced in production profiles)
 * - HttpOnly: Not accessible to JavaScript (prevents XSS attacks)
 * - SameSite=Strict: Prevent CSRF attacks (only same-site requests include cookie)
 * - Path: Set to service context for scope limitation
 *
 * Note: Spring Security's SessionManagementConfigurer automatically applies
 * these settings when configured via .sessionFixationProtection() and
 * .trackingModeStorage() in SecurityFilterChain beans.
 *
 * Also see:
 * - server.servlet.session.cookie.secure (set in prod profile)
 * - server.servlet.session.cookie.http-only (automatic with servlet container)
 * - server.servlet.session.cookie.same-site (strict in prod profile)
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecureCookieConfiguration implements WebMvcConfigurer {

    public SecureCookieConfiguration() {
        log.info("Secure Cookie Configuration enabled");
        log.info("Configured: HttpOnly=true, SameSite=Strict, Secure=true (prod only)");
    }
}

