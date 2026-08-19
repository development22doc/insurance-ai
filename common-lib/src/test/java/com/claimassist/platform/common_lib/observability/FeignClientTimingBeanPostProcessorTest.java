package com.claimassist.platform.common_lib.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.FeignClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeignClientTimingBeanPostProcessorTest {

    @FeignClient(name = "customer")
    interface CustomerClient {
        String findCustomer(Long id);
    }

    interface PlainService {
        String ping();
    }

    private FeignClientTimingBeanPostProcessor processor(BeanFactory bf) {
        return new FeignClientTimingBeanPostProcessor(bf);
    }

    @Test
    void nonFeignBean_returnsBeanUnchanged() {
        BeanFactory bf = mock(BeanFactory.class);
        FeignClientTimingBeanPostProcessor pp = processor(bf);

        PlainService original = new PlainService() {
            @Override
            public String ping() {
                return "pong";
            }
        };
        Object result = pp.postProcessAfterInitialization(original, "plain");

        assertThat(result).isSameAs(original);
    }

    @Test
    void feignBean_isWrappedAndTimed() {
        BeanFactory bf = mock(BeanFactory.class);
        PerformanceLogger pl = mock(PerformanceLogger.class);
        when(bf.getBean(PerformanceLogger.class)).thenReturn(pl);
        FeignClientTimingBeanPostProcessor pp = processor(bf);

        CustomerClient delegate = id -> "customer-" + id;
        Object wrapped = pp.postProcessAfterInitialization(delegate, "customerClient");

        assertThat(wrapped).isNotSameAs(delegate);
        assertThat(wrapped).isInstanceOf(CustomerClient.class);

        String result = ((CustomerClient) wrapped).findCustomer(42L);

        assertThat(result).isEqualTo("customer-42");
        verify(pl).log(eq("FEIGN"), any(), anyLong(), isNull());
    }

    @Test
    void feignBean_perfLoggerMissing_doesNotLog() {
        BeanFactory bf = mock(BeanFactory.class);
        when(bf.getBean(PerformanceLogger.class)).thenThrow(
                new org.springframework.beans.factory.NoSuchBeanDefinitionException(PerformanceLogger.class));
        FeignClientTimingBeanPostProcessor pp = processor(bf);

        CustomerClient delegate = id -> "customer-" + id;
        Object wrapped = pp.postProcessAfterInitialization(delegate, "customerClient");

        String result = ((CustomerClient) wrapped).findCustomer(7L);

        assertThat(result).isEqualTo("customer-7");
    }
}