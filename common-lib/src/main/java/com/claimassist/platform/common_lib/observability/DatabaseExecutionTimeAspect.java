package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * Aspect to measure execution time of repository/database operations. Targets
 * classes annotated with @Repository and methods annotated with
 * @Transactional to capture common database boundaries.
 */
@Aspect
@Order(250)
public class DatabaseExecutionTimeAspect {
    private final PerformanceLogger perfLogger;

    public DatabaseExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
    }

    @Around("within(@org.springframework.stereotype.Repository *) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object profileDb(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String op = pjp.getSignature().toShortString();
            perfLogger.log("DATABASE", op, elapsedMs, null);
        }
    }
}

