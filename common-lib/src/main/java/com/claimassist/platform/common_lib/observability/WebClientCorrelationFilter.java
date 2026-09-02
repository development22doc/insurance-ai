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
                // Route Object methods (e.g. hashCode/toString called by Spring during
                // bean lifecycle) to their default implementations so they do not
                // dereference a null argument array.
                switch (method.getName()) {
                    case "toString":
                        return "WebClientCorrelationFilter ExchangeFilterFunction";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        break;
                }

                // Only the functional method 'filter(ClientRequest, ExchangeFunction)'
                // carries the (request, exchangeFunction) arguments.
                if (!"filter".equals(method.getName()) || args == null || args.length < 2) {
                    throw new UnsupportedOperationException(
                            "Unsupported method on WebClientCorrelationFilter proxy: " + method.getName());
                }

                Object request = args[0];
                Object exchangeFunction = args[1];

                // ClientRequest.from(request) -> returns Builder
                Method fromMethod = clientRequestClass.getMethod("from", clientRequestClass);
                Object builder = fromMethod.invoke(null, request);

                // ClientRequest.Builder.header(String, String...) is a varargs method, so it must
                // be resolved with the String[] component type, not a second String (that exact-arity
                // lookup would throw NoSuchMethodException). Invoked with a one-element array below.
                Method headerMethod = builder.getClass().getMethod("header", String.class, String[].class);
                // DefaultClientRequestBuilder is package-private, so its public methods are not
                // callable across packages unless explicitly unlocked.
                headerMethod.setAccessible(true);

                String correlation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
                if (correlation != null) {
                    headerMethod.invoke(builder, LoggingConstants.CORRELATION_ID_HEADER, new String[]{correlation});
                }
                String requestId = MDC.get(LoggingConstants.MDC_REQUEST_ID);
                if (requestId != null) {
                    headerMethod.invoke(builder, LoggingConstants.REQUEST_ID_HEADER, new String[]{requestId});
                }
                String trace = MDC.get(LoggingConstants.MDC_TRACE_ID);
                if (trace != null) {
                    headerMethod.invoke(builder, LoggingConstants.TRACE_ID_HEADER, new String[]{trace});
                }

                // builder.build()
                Method buildMethod = builder.getClass().getMethod("build");
                buildMethod.setAccessible(true);
                Object newRequest = buildMethod.invoke(builder);

                // exchangeFunction.exchange(newRequest)
                Method exchangeMethod = exchangeFunctionClass.getMethod("exchange", clientRequestClass);
                return exchangeMethod.invoke(exchangeFunction, newRequest);
            }
        };

        return Proxy.newProxyInstance(cl, new Class[]{exchangeFilterFunction}, handler);
    }
}


