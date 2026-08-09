package com.claimassist.platform.common_lib.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the shared GlobalExceptionHandler that provides
 * centralized exception handling for all Servlet-based web applications.
 *
 * This configuration only registers a GlobalExceptionHandler bean if one
 * doesn't already exist. Individual services can provide their own implementation
 * by defining a GlobalExceptionHandler bean in their configuration.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.ServletException")
public class SharedExceptionAutoConfiguration {

    /**
     * Registers a default GlobalExceptionHandler bean if none already exists.
     * This bean can be overridden by services that provide their own
     * GlobalExceptionHandler bean configuration.
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
