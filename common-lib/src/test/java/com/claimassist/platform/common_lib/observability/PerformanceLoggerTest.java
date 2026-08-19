package com.claimassist.platform.common_lib.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PerformanceLoggerTest {

    private final EventLogger eventLogger = mock(EventLogger.class);
    private final PerformanceLoggingProperties props = new PerformanceLoggingProperties();

    @AfterEach
    void cleanup() {
        detach(LogCategories.LOGGER_BUSINESS);
        detach(LogCategories.LOGGER_PERFORMANCE);
        detach(LogCategories.LOGGER_DATABASE);
    }

    private ListAppender<ILoggingEvent> attach(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        logger.detachAndStopAllAppenders();
    }

    @Test
    void belowInfoThreshold_logsDebug_andEmitsPerformanceEvent() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log("BUSINESS", "policy.get", 100, Map.of("a", "b"));

        assertThat(events.list).isNotEmpty();
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(events.list.get(0).getFormattedMessage()).contains("policy.get");
        verify(eventLogger).logPerformanceEvent(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq(100L), any());
    }

    @Test
    void betweenInfoAndWarn_logsInfo() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log("BUSINESS", "op", 500, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void betweenWarnAndError_logsWarn() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log("BUSINESS", "op", 2000, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void aboveErrorThreshold_logsError() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log("BUSINESS", "op", 6000, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void categorySpecificOverride_wins() {
        PerformanceLoggingProperties.Thresholds t = new PerformanceLoggingProperties.Thresholds();
        t.setInfo(50);
        t.setWarn(100);
        t.setError(200);
        props.getCategories().put("DATABASE", t);
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_DATABASE);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log("DATABASE", "repo.find", 250, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void nullCategory_mapsToPerformanceLogger() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_PERFORMANCE);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        logger.log(null, "op", 100, Map.of());

        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void detailsValuesAreSerializedNullNumberString() {
        ListAppender<ILoggingEvent> events = attach(LogCategories.LOGGER_BUSINESS);
        PerformanceLogger logger = new PerformanceLogger(props, eventLogger);

        Map<String, Object> details = new HashMap<>();
        details.put("n", null);
        details.put("num", 5);
        details.put("s", "x");
        logger.log("BUSINESS", "op", 100, details);

        String msg = events.list.get(0).getFormattedMessage();
        assertThat(msg).contains("\"n\":null").contains("\"num\":5").contains("\"s\":\"x\"");
    }

    @Test
    void nullEventLogger_doesNotFail() {
        PerformanceLogger logger = new PerformanceLogger(props, null);

        logger.log("BUSINESS", "op", 100, Map.of("a", "b"));
    }
}