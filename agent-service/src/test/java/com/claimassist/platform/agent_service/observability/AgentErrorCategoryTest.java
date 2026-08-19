package com.claimassist.platform.agent_service.observability;

import com.claimassist.platform.agent_service.ai.tool.ToolCallLimitExceededException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionException;
import com.claimassist.platform.agent_service.ai.tool.ToolExecutionTimeoutException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentErrorCategoryTest {

    @Test
    void classifiesToolErrors() {
        assertThat(AgentErrorCategory.classify(new ToolExecutionException("get_claim_status", new IllegalStateException("boom"))))
                .isEqualTo(AgentErrorCategory.TOOL_ERROR);
        assertThat(AgentErrorCategory.classify(new ToolExecutionTimeoutException("get_claim_status", 5000)))
                .isEqualTo(AgentErrorCategory.TOOL_ERROR);
        assertThat(AgentErrorCategory.classify(new ToolCallLimitExceededException(10)))
                .isEqualTo(AgentErrorCategory.TOOL_ERROR);
    }

    @Test
    void classifiesTimeouts() {
        assertThat(AgentErrorCategory.classify(new TimeoutException("took too long")))
                .isEqualTo(AgentErrorCategory.TIMEOUT);
    }

    @Test
    void classifiesBackendUnavailableViaCauseChain() {
        Exception wrapped = new RuntimeException("downstream", new ConnectException("Connection refused"));
        assertThat(AgentErrorCategory.classify(wrapped)).isEqualTo(AgentErrorCategory.BACKEND_UNAVAILABLE);
    }

    @Test
    void defaultsToUnknown() {
        assertThat(AgentErrorCategory.classify(new IllegalArgumentException("noise")))
                .isEqualTo(AgentErrorCategory.UNKNOWN_ERROR);
        assertThat(AgentErrorCategory.classify(null)).isEqualTo(AgentErrorCategory.UNKNOWN_ERROR);
    }

    @Test
    void mapsResolverCodes() {
        assertThat(AgentErrorCategory.fromCode("OLLAMA_UNAVAILABLE")).isEqualTo(AgentErrorCategory.LLM_ERROR);
        assertThat(AgentErrorCategory.fromCode("MODEL_ERROR")).isEqualTo(AgentErrorCategory.LLM_ERROR);
        assertThat(AgentErrorCategory.fromCode("TOOL_EXECUTION_FAILED")).isEqualTo(AgentErrorCategory.TOOL_ERROR);
        assertThat(AgentErrorCategory.fromCode("AGENT_TIMEOUT")).isEqualTo(AgentErrorCategory.TIMEOUT);
        assertThat(AgentErrorCategory.fromCode("INPUT_REJECTED")).isEqualTo(AgentErrorCategory.PROMPT_GUARDRAIL_REJECTION);
        assertThat(AgentErrorCategory.fromCode("UNAUTHORIZED")).isEqualTo(AgentErrorCategory.AUTHORIZATION_ERROR);
        assertThat(AgentErrorCategory.fromCode("INVALID_TOOL_ARGUMENTS")).isEqualTo(AgentErrorCategory.VALIDATION_ERROR);
        assertThat(AgentErrorCategory.fromCode(null)).isEqualTo(AgentErrorCategory.UNKNOWN_ERROR);
        assertThat(AgentErrorCategory.fromCode("WEIRD")).isEqualTo(AgentErrorCategory.UNKNOWN_ERROR);
    }
}