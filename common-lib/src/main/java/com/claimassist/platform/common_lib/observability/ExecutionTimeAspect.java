package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Aspect that logs API execution time for controller methods. Delegates to
 * the shared PerformanceLogger so classification thresholds are consistent
 * and configurable.
 */
@Aspect
@Order(200)
public class ExecutionTimeAspect {
    // If created with a BeanFactory the PerformanceLogger lookup is deferred
    private volatile PerformanceLogger perfLogger;
    private final BeanFactory beanFactory;

    public ExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
        this.beanFactory = null;
    }

    public ExecutionTimeAspect(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.perfLogger = null;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object profile(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        Object ret = pjp.proceed();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        PerformanceLogger logger = resolvePerfLogger();
        if (logger != null) {
            try {
                // If the controller returned a reactive type, attach a completion
                // callback so the duration reflects the asynchronous execution.
                if (ret instanceof reactor.core.publisher.Mono<?> mono) {
                    return mono.doFinally(signal -> logger.log("REQUEST", method, elapsedMs, null));
                }
                if (ret instanceof reactor.core.publisher.Flux<?> flux) {
                    return flux.doFinally(signal -> logger.log("REQUEST", method, elapsedMs, null));
                }
            } catch (NoClassDefFoundError ignore) {
                // Reactor not on classpath for some modules - fall back
            }
            logger.log("REQUEST", method, elapsedMs, null);
        }
        return ret;
    }

    private PerformanceLogger resolvePerfLogger() {
        if (perfLogger != null) return perfLogger;
        if (beanFactory == null) return null;
        synchronized (this) {
            if (perfLogger == null) {
                try {
                    perfLogger = beanFactory.getBean(PerformanceLogger.class);
                } catch (NoSuchBeanDefinitionException ex) {
                    // No PerformanceLogger configured; behave gracefully
                    perfLogger = null;
                }
            }
        }
        return perfLogger;
    }
}

