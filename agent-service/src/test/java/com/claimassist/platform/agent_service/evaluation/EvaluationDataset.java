package com.claimassist.platform.agent_service.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the deterministic evaluation dataset from test resources
 * ({@code evaluation/agent-evaluation-cases.json}). No production data, secrets
 * or PII live in the dataset.
 */
public final class EvaluationDataset {

    private static final String RESOURCE = "/evaluation/agent-evaluation-cases.json";

    private EvaluationDataset() {}

    /** Load all cases from the JSON dataset. */
    public static List<EvaluationCase> load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = EvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return List.of();
            }
            List<Map<String, Object>> raw = mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            List<EvaluationCase> cases = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                cases.add(EvaluationCase.from(m));
            }
            return List.copyOf(cases);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load evaluation dataset " + RESOURCE, e);
        }
    }

    /** Find a case by id (null when absent). */
    public static EvaluationCase byId(String id) {
        return load().stream().filter(c -> id.equals(c.id())).findFirst().orElse(null);
    }
}