package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * Aspect to capture and log exceptions thrown by controllers and services. It
 * delegates structured details to ExceptionLoggingUtil.
 */
@Aspect
@Order(300)
public class ExceptionLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingAspect.class);

    @AfterThrowing(pointcut = "within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Service *)", throwing = "ex")
    public void logException(JoinPoint jp, Throwable ex) {
        try {
            ExceptionLoggingUtil.logException(jp, ex);
        } catch (Exception e) {
            log.error("Failed to log exception from aspect", e);
        }
    }
}

