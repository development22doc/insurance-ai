package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPlannerTest {

    private static final Set<String> ALL = Set.of(
            ToolRegistry.CLAIM_STATUS, ToolRegistry.POLICY_COVERAGE,
            ToolRegistry.CLAIM_DOCUMENTS, ToolRegistry.PROPOSE_CLAIM_UPDATE);

    @Test
    void multiPartQuestionPlansStatusAndDocumentsInOrder() {
        List<String> plan = ToolPlanner.plan(
                "What is my claim status and which documents have I submitted?", ALL);

        assertThat(plan).containsExactly(ToolRegistry.CLAIM_STATUS, ToolRegistry.CLAIM_DOCUMENTS);
    }

    @Test
    void singleToolQuestionPlansOnlyThatTool() {
        assertThat(ToolPlanner.plan("What is the current status of my claim?", ALL))
                .containsExactly(ToolRegistry.CLAIM_STATUS);
        assertThat(ToolPlanner.plan("What coverage do I have on my policy?", ALL))
                .containsExactly(ToolRegistry.POLICY_COVERAGE);
        assertThat(ToolPlanner.plan("Show me the documents I have submitted.", ALL))
                .containsExactly(ToolRegistry.CLAIM_DOCUMENTS);
    }

    @Test
    void writeToolIsNeverPlanned() {
        List<String> plan = ToolPlanner.plan(
                "Please propose moving my claim to DOCS_REQUESTED because my photos are blurry.", ALL);

        assertThat(plan).doesNotContain(ToolRegistry.PROPOSE_CLAIM_UPDATE).isEmpty();
    }

    @Test
    void unregisteredToolsAreNeverPlanned() {
        // Only the status tool is registered; the documents tool must not be planned.
        List<String> plan = ToolPlanner.plan(
                "What is my claim status and which documents have I submitted?",
                Set.of(ToolRegistry.CLAIM_STATUS));

        assertThat(plan).containsExactly(ToolRegistry.CLAIM_STATUS);
    }

    @Test
    void blankOrNullInputYieldsEmptyPlan() {
        assertThat(ToolPlanner.plan(null, ALL)).isEmpty();
        assertThat(ToolPlanner.plan("   ", ALL)).isEmpty();
    }

    @Test
    void heuristicMatchesAreDeterministic() {
        assertThat(ToolPlanner.plan("what is my claim status", ALL))
                .isEqualTo(ToolPlanner.plan("What is my claim status", ALL));
    }

    @Test
    void unrelatedProseYieldsEmptyPlan() {
        assertThat(ToolPlanner.plan("Say hello in one short sentence.", ALL)).isEmpty();
    }
}
