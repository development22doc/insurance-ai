package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * Aspect to measure execution time of Kafka listener methods annotated with
 * @KafkaListener. Reports timings to PerformanceLogger under the KAFKA
 * category.
 */
@Aspect
@Order(260)
public class KafkaExecutionTimeAspect {
    private final PerformanceLogger perfLogger;

    public KafkaExecutionTimeAspect(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
    }

    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object profileKafka(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String op = pjp.getSignature().toShortString();
            perfLogger.log("KAFKA", op, elapsedMs, null);
        }
    }
}

