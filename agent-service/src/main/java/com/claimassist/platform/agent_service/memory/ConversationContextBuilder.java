package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns bounded conversation history into LLM context. It keeps the exact
 * single-user-message + tool-callbacks ChatClient shape that Spring AI tool
 * calling depends on, by folding the prior conversation transcript into the
 * SYSTEM message (role-labelled and clearly delimited) rather than introducing
 * a multi-message prompt that could destabilize tool calling.
 * <p>
 * The history is bounded in TWO independent ways before reaching the model:
 * <ol>
 *   <li>message count - the memory service already caps the number of turns;</li>
 *   <li>character budget - this builder drops the OLDEST entries first so the
 *       transcript never exceeds {@code agent.ai.max-context-chars}.</li>
 * </ol>
 * Because this architecture has no exact token counter, the character budget is
 * a deliberately documented SAFE APPROXIMATION of a token budget (English text
 * averages well under one token per character), never a claim that characters
 * equal tokens.
 */
@Component
public class ConversationContextBuilder {

    private final AgentAiProperties properties;

    public ConversationContextBuilder(AgentAiProperties properties) {
        this.properties = properties;
    }

    public record Context(String systemPrompt, String userMessage) {}

    /**
     * Build the final prompt: the given system instructions plus a bounded
     * transcript of prior conversation (if any), and the current user message.
     */
    public Context build(String systemInstructions, String currentMessage, List<ConversationMessage> history) {
        String transcript = renderHistory(history);
        String combined = transcript.isEmpty()
                ? systemInstructions
                : systemInstructions + "\n\n" + transcript;
        return new Context(combined, currentMessage);
    }

    /**
     * Render the prior conversation as a bounded, role-labelled transcript.
     * Drops the oldest entries first when the character budget is exceeded,
     * always keeping at least the most recent turn.
     */
    public String renderHistory(List<ConversationMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int budget = properties.getMaxContextChars();
        List<String> lines = new ArrayList<>(history.size());
        int used = 0;
        // Walk newest -> oldest so the budget keeps the most recent context.
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessage m = history.get(i);
            String line = "[" + m.role() + "] " + m.content();
            if (used + line.length() > budget && !lines.isEmpty()) {
                break;
            }
            lines.add(line);
            used += line.length();
        }
        Collections.reverse(lines);
        return "## Previous conversation (context)\n" + String.join("\n", lines)
                + "\n## End of previous conversation";
    }
}