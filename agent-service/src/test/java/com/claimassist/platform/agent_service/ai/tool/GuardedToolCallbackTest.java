package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@link ToolCallback} decorator routes every invocation through
 * the guard (so calls are counted + time-bounded) while delegating the tool
 * definition unchanged.
 */
class GuardedToolCallbackTest {

    @Test
    void callRoutesThroughGuardAndDelegates() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("get_claim_status");
        when(delegate.call("{}")).thenReturn("{\"ok\":true}");

        GuardedToolCallback guarded = new GuardedToolCallback(delegate,
                new ToolExecutionGuard(new AgentAiProperties(), new ToolRegistry()));

        assertThat(guarded.call("{}")).isEqualTo("{\"ok\":true}");
        verify(delegate).call("{}");
    }

    @Test
    void callWithToolContextRoutesThroughGuard() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("propose_claim_update");
        ToolContext context = mock(ToolContext.class);
        when(delegate.call("{\"s\":\"A\"}", context)).thenReturn("{\"ok\":true}");

        GuardedToolCallback guarded = new GuardedToolCallback(delegate,
                new ToolExecutionGuard(new AgentAiProperties(), new ToolRegistry()));

        assertThat(guarded.call("{\"s\":\"A\"}", context)).isEqualTo("{\"ok\":true}");
        verify(delegate).call("{\"s\":\"A\"}", context);
    }

    @Test
    void propagatesToolCallLimitThroughGuard() {
        AgentAiProperties props = new AgentAiProperties();
        props.setMaxToolCalls(1);
        ToolExecutionGuard guard = new ToolExecutionGuard(props, new ToolRegistry());

        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("t");
        when(delegate.call(org.mockito.ArgumentMatchers.anyString())).thenReturn("x");

        GuardedToolCallback guarded = new GuardedToolCallback(delegate, guard);
        assertThat(guarded.call("a")).isEqualTo("x");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> guarded.call("b")))
                .isInstanceOf(ToolCallLimitExceededException.class);
    }

    @Test
    void getToolDefinitionDelegates() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(delegate.getToolDefinition()).thenReturn(definition);

        GuardedToolCallback guarded = new GuardedToolCallback(delegate,
                new ToolExecutionGuard(new AgentAiProperties(), new ToolRegistry()));
        assertThat(guarded.getToolDefinition()).isSameAs(definition);
    }
}
