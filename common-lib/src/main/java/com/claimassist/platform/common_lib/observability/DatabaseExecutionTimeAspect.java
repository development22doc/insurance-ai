package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Aspect to measure execution time of repository/database operations. Targets
 * classes annotated with @Repository and methods annotated with
 * @Transactional to capture common database boundaries.
 */
@Aspect
@Order(250)
public class DatabaseExecutionTimeAspect {
    private volatile PerformanceLogger perfLogger;
    private final BeanFactory beanFactory;

    public DatabaseExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
        this.beanFactory = null;
    }

    public DatabaseExecutionTimeAspect(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.perfLogger = null;
    }

    @Around("within(@org.springframework.stereotype.Repository *) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object profileDb(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String op = pjp.getSignature().toShortString();
            PerformanceLogger logger = resolvePerfLogger();
            if (logger != null) {
                logger.log("DATABASE", op, elapsedMs, null);
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

