package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

/**
 * Aspect that logs API execution time for controller methods. Delegates to
 * the shared PerformanceLogger so classification thresholds are consistent
 * and configurable.
 */
@Aspect
@Order(200)
public class ExecutionTimeAspect {
    private final PerformanceLogger perfLogger;

    public ExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object profile(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
            perfLogger.log("REQUEST", method, elapsedMs, null);
        }
    }
}

