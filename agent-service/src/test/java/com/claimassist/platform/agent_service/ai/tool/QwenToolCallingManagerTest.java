package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QwenToolCallingManagerTest {

    private QwenToolCallParser parser;
    private QwenToolCallingManager manager;
    private final TestTools tools = new TestTools();
    private final List<ToolExecutionMetadata> executions = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        parser = new QwenToolCallParser();
        manager = new QwenToolCallingManager(parser);
        tools.reset();
        executions.clear();
    }

    private List<ToolCallback> guardedCallbacks() {
        AgentAiProperties props = new AgentAiProperties();
        ToolExecutionGuard guard = new ToolExecutionGuard(props, new ToolRegistry(), "req", "corr", executions::add);
        ToolCallbackProvider guarded =
                guard.guarded(MethodToolCallbackProvider.builder().toolObjects(tools).build());
        return List.of(guarded.getToolCallbacks());
    }

    private Prompt promptWith(List<ToolCallback> callbacks, String userText) {
        ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .build();
        return new Prompt(List.of(new UserMessage(userText)), options);
    }

    private ChatResponse textResponse(String assistantText) {
        AssistantMessage am = AssistantMessage.builder().content(assistantText).properties(java.util.Map.of()).build();
        return new ChatResponse(List.of(new Generation(am)));
    }

    private ChatResponse nativeToolResponse() {
        AssistantMessage am = AssistantMessage.builder()
                .content("")
                .properties(java.util.Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall("id1", "function", "get_claim_status", "{}")))
                .build();
        return new ChatResponse(List.of(new Generation(am)));
    }

    @Test
    void nativeSpringAiToolCallStillWorks() {
        List<ToolCallback> callbacks = guardedCallbacks();
        var result = manager.executeToolCalls(promptWith(callbacks, "status"), nativeToolResponse());

        assertThat(result.conversationHistory()).hasSize(3); // user + assistant + tool response
        assertThat(result.conversationHistory().get(2)).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().get(2);
        assertThat(trm.getResponses().get(0).name()).isEqualTo("get_claim_status");
        assertThat(tools.getClaimStatusInvoked()).isTrue();
        // Native path still flows through the guard.
        assertThat(executions).isNotEmpty();
    }

    @Test
    void qwenTextToolCallExecutes() {
        List<ToolCallback> callbacks = guardedCallbacks();
        var result = manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("{\"name\":\"get_claim_status\",\"arguments\":{}}"));

        assertThat(result.conversationHistory()).hasSize(3);
        assertThat(result.conversationHistory().get(2)).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().get(2);
        assertThat(trm.getResponses().get(0).name()).isEqualTo("get_claim_status");
        assertThat(tools.getClaimStatusInvoked()).isTrue();
    }

    @Test
    void qwenTextToolCallGoesThroughToolExecutionGuard() {
        List<ToolCallback> callbacks = guardedCallbacks();
        manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("{\"name\":\"get_claim_status\",\"arguments\":{}}"));

        assertThat(executions).anyMatch(e -> "get_claim_status".equals(e.toolName())
                && ToolExecutionMetadata.STATUS_SUCCESS.equals(e.status()));
    }

    @Test
    void unauthorizedQwenToolCallIsRejectedBehindAutority() {
        // Simulate a tool whose own authorization check denies the request: the
        // tool method returns an UNAUTHORIZED outcome and never touches the backend.
        List<ToolCallback> callbacks = guardedCallbacks();
        tools.setDenyDocuments(true);

        var result = manager.executeToolCalls(
                promptWith(callbacks, "documents"),
                textResponse("{\"name\":\"get_claim_documents\",\"arguments\":{}}"));

        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().get(2);
        assertThat(trm.getResponses().get(0).responseData()).contains("UNAUTHORIZED");
        assertThat(tools.getDocumentsBackendInvoked()).isFalse();
    }

    @Test
    void unknownQwenToolCallIsRejected() {
        List<ToolCallback> callbacks = guardedCallbacks();
        assertThatThrownBy(() -> manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("{\"name\":\"java.lang.Runtime.exec\",\"arguments\":{}}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid registered tool");
        assertThat(tools.getClaimStatusInvoked()).isFalse();
    }

    @Test
    void malformedQwenToolCallIsRejected() {
        List<ToolCallback> callbacks = guardedCallbacks();
        assertThatThrownBy(() -> manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("I can help you with your claim.")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tools.getClaimStatusInvoked()).isFalse();
    }

    @Test
    void toolResultIsReturnedToModelViaConversationHistory() {
        List<ToolCallback> callbacks = guardedCallbacks();
        var result = manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("{\"name\":\"get_claim_status\",\"arguments\":{}}"));

        assertThat(result.returnDirect()).isFalse();
        assertThat(result.conversationHistory()).hasSize(3);
        // Assistant message precedes the tool response so the model can ground its answer.
        assertThat(result.conversationHistory().get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(result.conversationHistory().get(2)).isInstanceOf(ToolResponseMessage.class);
    }

    @Test
    void multipleSequentialToolCallsWork() {
        List<ToolCallback> callbacks = guardedCallbacks();
        var first = manager.executeToolCalls(
                promptWith(callbacks, "status"),
                textResponse("{\"name\":\"get_claim_status\",\"arguments\":{}}"));

        ToolExecutionResult second = manager.executeToolCalls(
                new Prompt(first.conversationHistory(), DefaultToolCallingChatOptions.builder()
                        .toolCallbacks(callbacks).build()),
                textResponse("{\"name\":\"get_claim_documents\",\"arguments\":{}}"));

        assertThat(second.conversationHistory()).hasSize(5);
        assertThat(tools.getClaimStatusInvoked()).isTrue();
        assertThat(tools.getDocumentsBackendInvoked()).isTrue();
        assertThat(executions).hasSize(2);
    }

    /** Minimal registered tool surface for manager-level tests. */
    static class TestTools {
        private boolean claimStatusInvoked;
        private boolean documentsBackendInvoked;
        private boolean denyDocuments;

        void reset() {
            claimStatusInvoked = false;
            documentsBackendInvoked = false;
            denyDocuments = false;
        }

        boolean getClaimStatusInvoked() {
            return claimStatusInvoked;
        }

        boolean getDocumentsBackendInvoked() {
            return documentsBackendInvoked;
        }

        void setDenyDocuments(boolean denyDocuments) {
            this.denyDocuments = denyDocuments;
        }

        @Tool(name = "get_claim_status", description = "Get claim status")
        public String getClaimStatus() {
            claimStatusInvoked = true;
            return "{\"status\":\"UNDER_REVIEW\"}";
        }

        @Tool(name = "get_claim_documents", description = "Get documents")
        public String getClaimDocuments() {
            if (denyDocuments) {
                // Simulates the tool method's authorization check failing.
                return "{\"success\":false,\"error\":\"UNAUTHORIZED\",\"message\":\"You are not authorized.\"}";
            }
            documentsBackendInvoked = true;
            return "{\"docs\":[\"police_report\"]}";
        }

        @Tool(name = "propose_claim_update", description = "Propose an update")
        public String proposeClaimUpdate(
                @ToolParam(description = "status") String proposedStatus,
                @ToolParam(description = "note") String note) {
            return "{\"proposedStatus\":\"" + proposedStatus + "\",\"note\":\"" + note + "\"}";
        }
    }
}