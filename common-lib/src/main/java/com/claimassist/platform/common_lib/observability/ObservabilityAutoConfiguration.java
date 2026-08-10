package com.claimassist.platform.common_lib.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;

/**
 * Auto-configuration that exposes reusable observability beans: rest
 * template customizer, webclient filter and feign interceptor as well as AOP
 * aspects. Downstream services can import this module and let Spring create
 * these beans automatically. Servlet-specific beans are registered separately
 * in ServletObservabilityAutoConfiguration.
 */
@Configuration
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
    public ExecutionTimeAspect executionTimeAspect(PerformanceLogger perfLogger) {
        return new ExecutionTimeAspect(perfLogger);
    }

    @Bean
    public ExceptionLoggingAspect exceptionLoggingAspect() {
        return new ExceptionLoggingAspect();
    }

    @Bean
    public com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger() {
        // Application/service names can be overridden by downstream apps if desired
        String svc = System.getProperty("spring.application.name", System.getenv("SPRING_APPLICATION_NAME"));
        String app = System.getProperty("spring.application.name", System.getenv("SPRING_APPLICATION_NAME"));
        if (svc == null) svc = "application";
        if (app == null) app = svc;
        return new com.claimassist.platform.common_lib.observability.event.DefaultEventLogger(svc, app);
    }

    @Bean
    public PerformanceLogger performanceLogger(com.claimassist.platform.common_lib.observability.event.EventLogger ev, PerformanceLoggingProperties props) {
        return new PerformanceLogger(props, ev);
    }

    @Bean
    public DatabaseExecutionTimeAspect databaseExecutionTimeAspect(PerformanceLogger perfLogger) {
        return new DatabaseExecutionTimeAspect(perfLogger);
    }

    @Bean
    public KafkaExecutionTimeAspect kafkaExecutionTimeAspect(PerformanceLogger perfLogger) {
        return new KafkaExecutionTimeAspect(perfLogger);
    }

    @Bean
    public static FeignClientTimingBeanPostProcessor feignClientTimingBeanPostProcessor(ObjectProvider<PerformanceLogger> perfLoggerProvider) {
        // Use ObjectProvider for lazy injection to allow this static factory method to be called early
        // during bean post processor registration without forcing instantiation of the configuration class
        PerformanceLogger perfLogger = perfLoggerProvider.getIfAvailable();
        if (perfLogger == null) {
            // If PerformanceLogger is not available, create a no-op processor
            perfLogger = new PerformanceLogger(new PerformanceLoggingProperties(), null);
        }
        return new FeignClientTimingBeanPostProcessor(perfLogger);
    }
}

