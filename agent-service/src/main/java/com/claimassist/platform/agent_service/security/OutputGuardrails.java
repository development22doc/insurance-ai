package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.config.AgentAiProperties;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application-side output guardrail. After the model finishes, it scans the
 * model's final text for assertions of a hard, irreversible business outcome
 * (e.g. "your claim has been approved") and cross-checks that against the
 * claim status actually returned by the claims system (a grounded fact the
 * request already holds).
 * <p>
 * If the model asserts a final decision that the grounded facts do NOT
 * support, it emits a single controlled, neutral advisory. It never rewrites
 * or re-emits the model's streamed tokens - this keeps the implementation
 * simple and robust instead of depending on a fragile full-text parser.
 */
public class OutputGuardrails {

    private static final Pattern HARD_CLAIM = Pattern.compile(
            "(?i)\\b(your claim|your policy)\\s+(has been|was|is|is now)\\s+(approved|denied|paid|rejected)\\b");

    /** Business outcomes that are final and must be grounded. */
    private static final Set<String> HARD_OUTCOMES =
            Set.of("APPROVED", "DENIED", "PAID", "REJECTED");

    private final AgentAiProperties properties;

    public OutputGuardrails(AgentAiProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns an advisory to append if the model asserted a hard outcome that
     * is not backed by {@code groundedStatuses}, or empty when the response is
     * safe / no hard outcome is asserted.
     */
    public Optional<String> advisory(String modelText, Collection<String> groundedStatuses) {
        if (!properties.isEnableOutputGuardrail() || modelText == null || modelText.isBlank()) {
            return Optional.empty();
        }
        Set<String> grounded = new HashSet<>();
        if (groundedStatuses != null) {
            for (String status : groundedStatuses) {
                if (status != null && !status.isBlank()) {
                    grounded.add(status.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        String asserted = findHardOutcome(modelText);
        if (asserted == null) {
            return Optional.empty();
        }
        if (grounded.contains(asserted)) {
            return Optional.empty();
        }
        return Optional.of(
                "Please verify with the claim system: I could not confirm this final decision from the available information.");
    }

    private String findHardOutcome(String text) {
        Matcher matcher = HARD_CLAIM.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String outcome = matcher.group(3).toUpperCase(Locale.ROOT);
        return HARD_OUTCOMES.contains(outcome) ? outcome : null;
    }
}