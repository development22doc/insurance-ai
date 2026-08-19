package com.claimassist.platform.common_lib.observability.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.claimassist.platform.common_lib.observability.LogCategories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEventLoggerTest {

    private final DefaultEventLogger logger = new DefaultEventLogger("customer-service", "customer-service");

    @AfterEach
    void cleanup() {
        detach(LogCategories.LOGGER_BUSINESS);
        detach(LogCategories.LOGGER_SECURITY);
        detach(LogCategories.LOGGER_PERFORMANCE);
        detach(LogCategories.LOGGER_EXCEPTION);
        MDC.clear();
    }

    private ListAppender<ILoggingEvent> attach(String name) {
        Logger l = (Logger) LoggerFactory.getLogger(name);
        l.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        l.addAppender(appender);
        return appender;
    }

    private void detach(String name) {
        ((Logger) LoggerFactory.getLogger(name)).detachAndStopAllAppenders();
    }

    @Test
    void businessEvent_routesToBusinessLogger() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);

        logger.logBusinessEvent("customer-service", "customer-service", Map.of("k", "v"));

        assertThat(events.list).isNotEmpty();
        assertThat(events.list.get(0).getFormattedMessage()).contains("\"eventType\":\"BUSINESS\"");
    }

    @Test
    void securityEvent_routesToSecurityLoggerAtError() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_SECURITY);

        logger.logSecurityEvent("s", "a", Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.list.get(0).getFormattedMessage()).contains("\"eventType\":\"SECURITY\"");
    }

    @Test
    void performanceEvent_routesToPerformanceLoggerAtInfo() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_PERFORMANCE);

        logger.logPerformanceEvent("s", "a", 5L, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(events.list.get(0).getFormattedMessage()).contains("\"eventType\":\"PERFORMANCE\"");
    }

    @Test
    void exceptionEvent_routesToExceptionLoggerAtError() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_EXCEPTION);

        logger.logExceptionEvent("s", "a", Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.list.get(0).getFormattedMessage()).contains("\"eventType\":\"EXCEPTION\"");
    }

    @Test
    void nullServiceApplication_useDefaults() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);

        logger.logBusinessEvent(null, null, Map.of());

        assertThat(events.list.get(0).getFormattedMessage()).contains("\"service\":\"customer-service\"");
    }

    @Test
    void mdcCorrelationId_isIncludedWhenNotExplicit() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        MDC.put("correlationId", "corr-mdc");

        logger.logBusinessEvent("s", "a", Map.of());

        assertThat(events.list.get(0).getFormattedMessage()).contains("\"correlationId\":\"corr-mdc\"");
    }
}