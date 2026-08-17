package com.claimassist.platform.api_gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.WebFilter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the gateway startup failure caused by a bean-name collision:
 * the {@code CorrelationIdWebFilter} {@code @Configuration} class used to also register
 * a {@code @Bean} method named {@code correlationIdWebFilter()}, producing two bean
 * definitions with the same name and triggering a {@code BeanDefinitionOverrideException}.
 *
 * <p>This is a unit-style context test. It loads only the {@code CorrelationIdWebFilter}
 * configuration class via a plain Spring {@code ApplicationContext} and asserts that exactly
 * one {@link WebFilter} bean is registered. Using {@code @ContextConfiguration} (rather than
 * {@code @SpringBootTest}) means no Spring Boot {@code application.yaml} is loaded, so no
 * Config Server / Discovery / external infrastructure is required. If a duplicate bean
 * registration were reintroduced, context startup would fail with
 * {@code BeanDefinitionOverrideException} and this test would fail.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CorrelationIdWebFilter.class)
class CorrelationIdWebFilterRegistrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void registersExactlyOneWebFilterBean() {
        Map<String, WebFilter> filters = context.getBeansOfType(WebFilter.class);
        assertThat(filters).hasSize(1);
        assertThat(filters).containsOnlyKeys("correlationWebFilter");
    }
}