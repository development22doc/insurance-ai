package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.ai.tool.QwenToolCallParser;
import com.claimassist.platform.agent_service.ai.tool.QwenToolCallingManager;
import com.claimassist.platform.agent_service.ai.tool.QwenToolExecutionEligibilityPredicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QwenToolCallingConfigTest {

    private final QwenToolCallingConfig config = new QwenToolCallingConfig();

    @Test
    void exposesParser() {
        assertThat(config.qwenToolCallParser()).isInstanceOf(QwenToolCallParser.class);
    }

    @Test
    void exposesToolCallingManagerWiredToParser() {
        QwenToolCallParser parser = config.qwenToolCallParser();
        QwenToolCallingManager manager = config.qwenToolCallingManager(parser);
        assertThat(manager).isNotNull();
    }

    @Test
    void exposesEligibilityPredicate() {
        QwenToolCallParser parser = config.qwenToolCallParser();
        assertThat(config.qwenToolExecutionEligibilityPredicate(parser))
                .isInstanceOf(QwenToolExecutionEligibilityPredicate.class);
    }
}