package com.claimassist.platform.common_lib.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionTimeAspectTest {

    private ProceedingJoinPoint pjp() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getDeclaringType()).thenReturn(Object.class);
        when(sig.getName()).thenReturn("toString");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenReturn("result");
        return pjp;
    }

    @Test
    void profile_logsAndPropagatesResult() throws Throwable {
        PerformanceLogger pl = mock(PerformanceLogger.class);
        ExecutionTimeAspect aspect = new ExecutionTimeAspect(pl);

        Object result = aspect.profile(pjp());

        assertThat(result).isEqualTo("result");
        verify(pl).log(eq("REQUEST"), eq("Object.toString"), anyLong(), isNull());
    }

    @Test
    void profile_resolvesPerfLoggerLazilyFromBeanFactory() throws Throwable {
        PerformanceLogger pl = mock(PerformanceLogger.class);
        BeanFactory bf = mock(BeanFactory.class);
        when(bf.getBean(PerformanceLogger.class)).thenReturn(pl);
        ExecutionTimeAspect aspect = new ExecutionTimeAspect(bf);

        aspect.profile(pjp());

        verify(pl).log(eq("REQUEST"), any(), anyLong(), isNull());
    }

    @Test
    void profile_noPerfLoggerAvailable_doesNothing() throws Throwable {
        BeanFactory bf = mock(BeanFactory.class);
        when(bf.getBean(PerformanceLogger.class)).thenThrow(NoSuchBeanDefinitionException.class);
        ExecutionTimeAspect aspect = new ExecutionTimeAspect(bf);

        Object result = aspect.profile(pjp());

        assertThat(result).isEqualTo("result");
    }

    @Test
    void profileDb_logsDatabaseCategory() throws Throwable {
        PerformanceLogger pl = mock(PerformanceLogger.class);
        DatabaseExecutionTimeAspect aspect = new DatabaseExecutionTimeAspect(pl);

        Object result = aspect.profileDb(pjp());

        assertThat(result).isEqualTo("result");
        verify(pl).log(eq("DATABASE"), any(), anyLong(), isNull());
    }

    @Test
    void profileDb_noPerfLogger_doesNothing() throws Throwable {
        BeanFactory bf = mock(BeanFactory.class);
        when(bf.getBean(PerformanceLogger.class)).thenThrow(NoSuchBeanDefinitionException.class);
        DatabaseExecutionTimeAspect aspect = new DatabaseExecutionTimeAspect(bf);

        assertThat(aspect.profileDb(pjp())).isEqualTo("result");
    }

    @Test
    void profileKafka_logsKafkaCategory() throws Throwable {
        PerformanceLogger pl = mock(PerformanceLogger.class);
        KafkaExecutionTimeAspect aspect = new KafkaExecutionTimeAspect(pl);

        Object result = aspect.profileKafka(pjp());

        assertThat(result).isEqualTo("result");
        verify(pl).log(eq("KAFKA"), any(), anyLong(), isNull());
    }

    @Test
    void profileKafka_noPerfLogger_doesNothing() throws Throwable {
        PerformanceLogger pl = mock(PerformanceLogger.class);
        KafkaExecutionTimeAspect aspect = new KafkaExecutionTimeAspect(pl);

        assertThat(aspect.profileKafka(pjp())).isEqualTo("result");
    }
}