package com.claimassist.platform.common_lib.observability;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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
 *
 * IMPORTANT: Uses BeanFactory for lazy resolution of PerformanceLogger
 * to avoid eagerly instantiating dependencies during BeanPostProcessor
 * registration phase, which would trigger early bean creation warnings.
 * PerformanceLogger is looked up at processing time, not at BeanPostProcessor
 * construction time.
 */
// Not a component: bean is registered explicitly in ObservabilityAutoConfiguration
public class FeignClientTimingBeanPostProcessor implements BeanPostProcessor {
    private final BeanFactory beanFactory;

    public FeignClientTimingBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Do NOT resolve PerformanceLogger at wrapping time: that can trigger
        // early bean creation or circular references. Instead create an
        // InvocationHandler that lazily resolves and caches PerformanceLogger
        // on first actual method invocation.

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

        // Lazy, cached resolution of PerformanceLogger inside the handler
        final Object lock = new Object();
        final Holder perfHolder = new Holder();

        InvocationHandler handler = (proxy, method, args) -> {
            long start = System.nanoTime();
            try {
                return method.invoke(bean, args);
            } finally {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                String op = bean.getClass().getName() + ":" + method.getName();
                PerformanceLogger pl = perfHolder.value;
                if (pl == null) {
                    synchronized (lock) {
                        if (perfHolder.value == null) {
                            try {
                                perfHolder.value = beanFactory.getBean(PerformanceLogger.class);
                            } catch (NoSuchBeanDefinitionException | org.springframework.beans.factory.BeanCurrentlyInCreationException ex) {
                                // PerformanceLogger not available yet or being created; skip logging for now
                                perfHolder.value = null;
                            }
                        }
                        pl = perfHolder.value;
                    }
                }
                if (pl != null) {
                    pl.log("FEIGN", op, elapsedMs, null);
                }
            }
        };

        return Proxy.newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }

    // simple holder to allow modification inside lambda
    private static class Holder { volatile PerformanceLogger value; }
}

