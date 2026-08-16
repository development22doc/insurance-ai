package com.claimassist.platform.agent_service.evaluation;

import java.util.Map;

/**
 * One deterministic evaluation case (Phase 6.23). Loaded from the JSON dataset
 * in test resources (no production customer data, no secrets, no PII). Each
 * case declares the expected structural behaviour for a given input so that an
 * evaluation can assert meaningful, deterministic outcomes rather than exact
 * (fragile) natural-language equality.
 */
public record EvaluationCase(
        String id,
        String description,
        String input,
        EvaluationCategory category,
        String expectedTool,
        String expectedToolArgument,
        boolean expectedDenied,
        boolean expectedGuardrailRejected,
        boolean expectedContextUsed,
        String expectedErrorCategory) {

    /** Create a case from the JSON representation. */
    public static EvaluationCase from(Map<String, Object> m) {
        return new EvaluationCase(
                str(m, "id"),
                str(m, "description"),
                str(m, "input"),
                enumOf(m.get("category")),
                str(m, "expectedTool"),
                str(m, "expectedToolArgument"),
                bool(m.get("expectedDenied")),
                bool(m.get("expectedGuardrailRejected")),
                bool(m.get("expectedContextUsed")),
                str(m, "expectedErrorCategory"));
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean bool(Object v) {
        return Boolean.TRUE.equals(v);
    }

    private static EvaluationCategory enumOf(Object v) {
        if (v == null) {
            return EvaluationCategory.REGRESSION;
        }
        try {
            return EvaluationCategory.valueOf(v.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EvaluationCategory.REGRESSION;
        }
    }
}