package com.claimassist.platform.common_lib.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import feign.RequestInterceptor;

/**
 * Auto-configuration that exposes reusable observability beans: rest
 * template customizer, webclient filter and feign interceptor as well as AOP
 * aspects. Downstream services can import this module and let Spring create
 * these beans automatically. Servlet-specific beans are registered separately
 * in ServletObservabilityAutoConfiguration.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PerformanceLoggingProperties.class)
public class ObservabilityAutoConfiguration {


    @Bean("observabilityRestTemplateCustomizer")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(RestTemplateCustomizer.class)
    public RestTemplateCustomizer observabilityRestTemplateCustomizer() {
        return restTemplate -> restTemplate.getInterceptors().add(new RestTemplateCorrelationInterceptor());
    }

    @Bean
    public Object webClientCorrelationFilter() {
        try {
            return new WebClientCorrelationFilter().create();
        } catch (Throwable t) {
            // WebFlux not present on the classpath; return null so downstream apps
            // that don't use WebClient won't fail. Spring will ignore null beans.
            return null;
        }
    }

    @Bean
    public RequestInterceptor feignCorrelationRequestInterceptor() {
        return new FeignCorrelationRequestInterceptor();
    }

    @Bean
    public ExecutionTimeAspect executionTimeAspect(BeanFactory beanFactory) {
        // Use BeanFactory to avoid forcing PerformanceLogger creation during
        // configuration time. ExecutionTimeAspect will lazily resolve
        // PerformanceLogger when first needed.
        return new ExecutionTimeAspect(beanFactory);
    }

    @Bean
    public ExceptionLoggingAspect exceptionLoggingAspect() {
        return new ExceptionLoggingAspect();
    }

    @Bean
    @Lazy
    public com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger() {
        // Application/service names can be overridden by downstream apps if desired
        String svc = System.getProperty("spring.application.name", System.getenv("SPRING_APPLICATION_NAME"));
        String app = System.getProperty("spring.application.name", System.getenv("SPRING_APPLICATION_NAME"));
        if (svc == null) svc = "application";
        if (app == null) app = svc;
        return new com.claimassist.platform.common_lib.observability.event.DefaultEventLogger(svc, app);
    }

    @Bean
    @Lazy
    public PerformanceLogger performanceLogger(com.claimassist.platform.common_lib.observability.event.EventLogger ev, PerformanceLoggingProperties props) {
        return new PerformanceLogger(props, ev);
    }

    @Bean
    public DatabaseExecutionTimeAspect databaseExecutionTimeAspect(BeanFactory beanFactory) {
        return new DatabaseExecutionTimeAspect(beanFactory);
    }

    @Bean
    public KafkaExecutionTimeAspect kafkaExecutionTimeAspect(BeanFactory beanFactory) {
        return new KafkaExecutionTimeAspect(beanFactory);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static FeignClientTimingBeanPostProcessor feignClientTimingBeanPostProcessor(BeanFactory beanFactory) {
        // Pass BeanFactory for lazy lookup at processing time
        // This avoids injecting PerformanceLogger as a dependency, preventing early
        // instantiation during BeanPostProcessor registration phase
        return new FeignClientTimingBeanPostProcessor(beanFactory);
    }
}
