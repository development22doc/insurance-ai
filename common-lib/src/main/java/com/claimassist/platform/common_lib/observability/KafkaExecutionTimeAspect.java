package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Aspect to measure execution time of Kafka listener methods annotated with
 * @KafkaListener. Reports timings to PerformanceLogger under the KAFKA
 * category.
 */
@Aspect
@Order(260)
public class KafkaExecutionTimeAspect {
    private volatile PerformanceLogger perfLogger;
    private final BeanFactory beanFactory;

    public KafkaExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
        this.beanFactory = null;
    }

    public KafkaExecutionTimeAspect(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.perfLogger = null;
    }

    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object profileKafka(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String op = pjp.getSignature().toShortString();
            PerformanceLogger logger = resolvePerfLogger();
            if (logger != null) {
                logger.log("KAFKA", op, elapsedMs, null);
            }
        }
    }

    private PerformanceLogger resolvePerfLogger() {
        if (perfLogger != null) return perfLogger;
        if (beanFactory == null) return null;
        synchronized (this) {
            if (perfLogger == null) {
                try {
                    perfLogger = beanFactory.getBean(PerformanceLogger.class);
                } catch (NoSuchBeanDefinitionException ex) {
                    perfLogger = null;
                }
            }
        }
        return perfLogger;
    }
}

