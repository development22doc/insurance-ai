package com.claimassist.platform.common_lib.observability;

import org.slf4j.MDC;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Factory that produces a WebClient ExchangeFilterFunction via reflection so
 * that `common-lib` does not require Spring WebFlux at compile time. If
 * WebFlux is not present this class returns null from {@link #create()}.
 */
public class WebClientCorrelationFilter {
    public WebClientCorrelationFilter() {}

    /**
     * Create an instance of ExchangeFilterFunction if WebClient is available on
     * the classpath; otherwise return null.
     */
    public Object create() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> exchangeFilterFunction = Class.forName("org.springframework.web.reactive.function.client.ExchangeFilterFunction", true, cl);
        Class<?> clientRequestClass = Class.forName("org.springframework.web.reactive.function.client.ClientRequest", true, cl);
        Class<?> exchangeFunctionClass = Class.forName("org.springframework.web.reactive.function.client.ExchangeFunction", true, cl);

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // method is 'filter(ClientRequest, ExchangeFunction)'
                Object request = args[0];
                Object exchangeFunction = args[1];

                // ClientRequest.from(request) -> returns Builder
                Method fromMethod = clientRequestClass.getMethod("from", clientRequestClass);
                Object builder = fromMethod.invoke(null, request);

                // builder.header(String, String)
                Method headerMethod = builder.getClass().getMethod("header", String.class, String.class);

                String correlation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
                if (correlation != null) headerMethod.invoke(builder, LoggingConstants.CORRELATION_ID_HEADER, correlation);
                String trace = MDC.get(LoggingConstants.MDC_TRACE_ID);
                if (trace != null) headerMethod.invoke(builder, LoggingConstants.TRACE_ID_HEADER, trace);

                // builder.build()
                Method buildMethod = builder.getClass().getMethod("build");
                Object newRequest = buildMethod.invoke(builder);

                // exchangeFunction.exchange(newRequest)
                Method exchangeMethod = exchangeFunctionClass.getMethod("exchange", clientRequestClass);
                return exchangeMethod.invoke(exchangeFunction, newRequest);
            }
        };

        return Proxy.newProxyInstance(cl, new Class[]{exchangeFilterFunction}, handler);
    }
}


