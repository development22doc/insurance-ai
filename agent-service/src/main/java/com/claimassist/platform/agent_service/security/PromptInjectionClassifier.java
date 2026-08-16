package com.claimassist.platform.agent_service.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Balanced, heuristic prompt-injection detector for free-text user messages.
 * <p>
 * It looks for a SMALL set of high-signal intents - overriding instructions,
 * extracting the hidden system prompt, or accessing another user's data -
 * rather than a broad keyword blacklist. Normal insurance questions such as
 * "Why was my claim rejected?", "Show me my claim status" or "What documents
 * are required?" are deliberately NOT flagged.
 * <p>
 * SECURITY NOTE: this is a hardening layer, NOT the security boundary. Because
 * the detector is heuristic (and could in principle miss or false-positive),
 * every request is still independently gated by application-level
 * authentication, {@code canAccessClaim} authorization, tool permission checks
 * and resource ownership. A detected signal triggers a fail-closed rejection:
 * the LLM is never invoked with the flagged input.
 */
public final class PromptInjectionClassifier {

    private static final List<Pattern> INSTRUCTION_OVERRIDE = compile(
            "ignore\\s+(?:(?:all|any|your|previous|prior|earlier)\\s+)*instructions",
            "ignore\\s+(the\\s+|your\\s+)?(system\\s+|developer\\s+)?prompt",
            "ignore\\s+(your\\s+)?rules",
            "disregard\\s+(?:(?:your|all|any|previous|prior|earlier)\\s+)*instructions",
            "forget\\s+(?:(?:all|your|any|previous|prior|earlier)\\s+)*instructions",
            "forget\\s+(your\\s+)?(system\\s+|developer\\s+)?prompt",
            "override\\s+(your\\s+|the\\s+)?instructions",
            "you\\s+are\\s+now\\s+(without\\s+instructions|no\\s+longer)",
            "act\\s+as\\s+if\\s+(you\\s+have\\s+no\\s+instructions|your\\s+instructions\\s+don't\\s+apply)",
            "use\\s+(these|my|this)\\s+instructions\\s+instead",
            "do\\s+not\\s+follow\\s+your\\s+(instructions|rules|guidelines|system\\s+prompt)",
            "secret\\s+instructions|hidden\\s+instructions");

    private static final List<Pattern> SYSTEM_DISCLOSURE = compile(
            "reveal\\s+(your\\s+|the\\s+)?(system\\s+|developer\\s+)?(prompt|instructions|system\\s+message)",
            "show\\s+(me\\s+)?(your\\s+|the\\s+)?(system\\s+|developer\\s+)?(prompt|instructions)",
            "print\\s+(your\\s+|the\\s+)?(system\\s+|developer\\s+)?(prompt|instructions)",
            "what\\s+(are|is)\\s+(your|the)\\s+(system\\s+|developer\\s+)?(instructions|prompt)",
            "expose\\s+(your\\s+|the\\s+)?system\\s+prompt",
            "share\\s+(your\\s+|the\\s+)?system\\s+prompt",
            "(tell|write|output)\\s+(me\\s+)?your\\s+(system\\s+|developer\\s+)?(instructions|prompt)");

    private static final List<Pattern> UNAUTHORIZED_ACCESS = compile(
            "ignore\\s+(your\\s+)?(security|authorization|permissions)",
            "bypass\\s+(the\\s+)?(security|authorization|permission)",
            "disable\\s+(the\\s+)?(security|authorization|permission)",
            "without\\s+(authorization|permission)",
            "another\\s+(customer'?s?|user'?s?|person'?s?)\\s+(claim|policy|account|data)",
            "other\\s+(customer'?s?|user'?s?|person'?s?)\\s+(claim|policy)",
            "someone\\s+else'?s?\\s+(claim|policy|account|data)",
            "any\\s+(customer|user|claim|policy)\\s+(data|information)",
            "(get_claim_documents|get_claim_status|get_policy_coverage|propose_claim_update)\\s+(for|on)\\s+(another|other|someone)");

    private PromptInjectionClassifier() {}

    /**
     * Classify the given user message. Never returns null.
     */
    public static PromptInjectionSignal classify(String message) {
        if (message == null) {
            return PromptInjectionSignal.NONE;
        }
        String text = message.toLowerCase(Locale.ROOT);
        for (Pattern pattern : UNAUTHORIZED_ACCESS) {
            if (pattern.matcher(text).find()) {
                return PromptInjectionSignal.UNAUTHORIZED_ACCESS;
            }
        }
        for (Pattern pattern : SYSTEM_DISCLOSURE) {
            if (pattern.matcher(text).find()) {
                return PromptInjectionSignal.SYSTEM_DISCLOSURE;
            }
        }
        for (Pattern pattern : INSTRUCTION_OVERRIDE) {
            if (pattern.matcher(text).find()) {
                return PromptInjectionSignal.INSTRUCTION_OVERRIDE;
            }
        }
        return PromptInjectionSignal.NONE;
    }

    private static List<Pattern> compile(String... regexes) {
        return java.util.Arrays.stream(regexes)
                .map(Pattern::compile)
                .toList();
    }
}