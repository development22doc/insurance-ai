package org.springframework.cloud.openfeign;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Test-only stand-in for Spring Cloud OpenFeign's @FeignClient so
 * FeignClientTimingBeanPostProcessor (which detects the annotation
 * reflectively by name) can be exercised in unit tests without pulling the
 * full OpenFeign dependency onto common-lib's production classpath.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FeignClient {
    String value() default "";
    String name() default "";
}