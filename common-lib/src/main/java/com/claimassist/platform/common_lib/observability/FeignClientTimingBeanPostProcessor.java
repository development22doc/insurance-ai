package com.claimassist.platform.common_lib.observability;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * BeanPostProcessor that wraps Feign client beans (interfaces annotated with
 * @FeignClient) with a dynamic proxy that measures method execution time and
 * reports it to PerformanceLogger. This approach avoids depending on Feign
 * internals and works with Spring-managed Feign client beans.
 */
// Not a component: bean is registered explicitly in ObservabilityAutoConfiguration
public class FeignClientTimingBeanPostProcessor implements BeanPostProcessor {
    private final PerformanceLogger perfLogger;

    public FeignClientTimingBeanPostProcessor(PerformanceLogger perfLogger) {
        this.perfLogger = perfLogger;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces == null || interfaces.length == 0) return bean;

        boolean isFeign = false;
        for (Class<?> iface : interfaces) {
            // Check for presence of FeignClient annotation reflectively to avoid a compile-time
            // dependency on Spring Cloud OpenFeign.
            for (java.lang.annotation.Annotation a : iface.getAnnotations()) {
                if (a.annotationType().getName().equals("org.springframework.cloud.openfeign.FeignClient")) {
                    isFeign = true; break;
                }
            }
            if (isFeign) break;
        }
        if (!isFeign) return bean;

        InvocationHandler handler = (proxy, method, args) -> {
            long start = System.nanoTime();
            try {
                return method.invoke(bean, args);
            } finally {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                String op = bean.getClass().getName() + ":" + method.getName();
                perfLogger.log("FEIGN", op, elapsedMs, null);
            }
        };

        return Proxy.newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }
}

