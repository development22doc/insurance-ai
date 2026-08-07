package com.claimassist.platform.common_lib.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;

/**
 * Servlet-specific observability auto-configuration. Registers servlet
 * CorrelationIdFilter only when servlet stack is available.
 */
@Configuration
@ConditionalOnClass(Filter.class)
public class ServletObservabilityAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        FilterRegistrationBean<CorrelationIdFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("observabilityCorrelationIdFilter");
        return reg;
    }
}

