package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QwenToolCallParserTest {

    private static final Set<String> REGISTERED = Set.of(
            "get_claim_status", "get_policy_coverage", "get_claim_documents", "propose_claim_update");

    private final QwenToolCallParser parser = new QwenToolCallParser();

    private Optional<QwenToolCall> parse(String content) {
        return parser.parse(content, REGISTERED);
    }

    @Test
    void parsesValidGetClaimStatus() {
        Optional<QwenToolCall> result = parse("{\"name\":\"get_claim_status\",\"arguments\":{}}");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("get_claim_status");
        assertThat(result.get().arguments()).isEmpty();
    }

    @Test
    void parsesValidGetPolicyCoverage() {
        Optional<QwenToolCall> result = parse("{\"name\":\"get_policy_coverage\",\"arguments\":{}}");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("get_policy_coverage");
    }

    @Test
    void parsesValidGetClaimDocuments() {
        Optional<QwenToolCall> result = parse("{\"name\":\"get_claim_documents\",\"arguments\":{}}");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("get_claim_documents");
    }

    @Test
    void parsesValidProposeClaimUpdate() {
        Optional<QwenToolCall> result = parse(
                "{\"name\":\"propose_claim_update\",\"arguments\":{\"proposedStatus\":\"DOCS_REQUESTED\",\"note\":\"blurry photos\"}}");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("propose_claim_update");
        assertThat(result.get().arguments())
                .containsEntry("proposedStatus", "DOCS_REQUESTED")
                .containsEntry("note", "blurry photos");
    }

    @Test
    void rejectsMalformedJson() {
        assertThat(parse("{not json")).isEmpty();
        assertThat(parse("[1,2")).isEmpty();
        assertThat(parse("{\"name\": \"get_claim_status\", arguments: {}}")).isEmpty();
    }

    @Test
    void rejectsUnknownTool() {
        assertThat(parse("{\"name\":\"java.lang.Runtime.exec\",\"arguments\":{}}")).isEmpty();
        assertThat(parse("{\"name\":\"rm -rf /\",\"arguments\":{}}")).isEmpty();
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":{},\"name\":\"evil\"}")).isEmpty();
    }

    @Test
    void rejectsMissingName() {
        assertThat(parse("{\"arguments\":{}}")).isEmpty();
    }

    @Test
    void rejectsMissingArguments() {
        assertThat(parse("{\"name\":\"get_claim_status\"}")).isEmpty();
    }

    @Test
    void rejectsNonObjectArguments() {
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":[]}")).isEmpty();
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":\"text\"}")).isEmpty();
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":7}")).isEmpty();
    }

    @Test
    void rejectsUnexpectedArgumentsField() {
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":{},\"extra\":1}")).isEmpty();
    }

    @Test
    void rejectsInvalidNameType() {
        assertThat(parse("{\"name\":42,\"arguments\":{}}")).isEmpty();
    }

    @Test
    void rejectsOversizedInput() {
        QwenToolCallParser tiny = new QwenToolCallParser(10);
        String longJson = "{\"name\":\"get_claim_status\",\"arguments\":{\"note\":\"" + "x".repeat(200) + "\"}}";
        assertThat(tiny.parse(longJson, REGISTERED)).isEmpty();
        assertThat(tiny.classify(longJson, REGISTERED)).isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void ignoresOrdinaryProse() {
        assertThat(parse("Your claim is currently under review. We need one more document.")).isEmpty();
        assertThat(parse("The")).isEmpty();
    }

    @Test
    void ignoresJsonThatIsNotToolCallObject() {
        assertThat(parse("{\"foo\":\"bar\"}")).isEmpty();
        assertThat(parse("[1,2,3]")).isEmpty();
        assertThat(parse("\"just a string\"")).isEmpty();
        assertThat(parse("{\"name\":\"not_a_tool\",\"arguments\":{}}")).isEmpty();
    }

    @Test
    void handlesEscapedJsonCorrectly() {
        String escaped = "{\"name\":\"propose_claim_update\",\"arguments\":{\"proposedStatus\":\"DOCS_REQUESTED\",\"note\":\"photos are \\\"too blurry\\\" now\"}}";
        Optional<QwenToolCall> result = parse(escaped);
        assertThat(result).isPresent();
        assertThat(result.get().arguments().get("note")).isEqualTo("photos are \"too blurry\" now");
    }

    @Test
    void rejectsDuplicateJsonProperties() {
        assertThat(parse("{\"name\":\"get_claim_status\",\"arguments\":{},\"name\":\"get_policy_coverage\"}")).isEmpty();
    }

    @Test
    void classifyDetectsCrossChunkPrefixAndCompleteObject() {
        String complete = "{\"name\":\"get_claim_status\",\"arguments\":{}}";
        // Accumulate a partial prefix first.
        assertThat(parser.classify("{\"name\":\"get_c", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
        // Once complete, it is a TOOL_CALL.
        assertThat(parser.classify(complete, REGISTERED)).isEqualTo(QwenToolCallParser.Classification.TOOL_CALL);
        // Ordinary prose is NOT_A_TOOL_CALL immediately.
        assertThat(parser.classify("The status", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void classifyKeepsObjectWithKeySeparatorCommaAsPossiblePrefix() {
        // Regression: Jackson reports a partially-read object ending after a key-separator
        // comma (before EOF) as a MismatchedInputException, not JsonEOFException. It must
        // still be treated as an incomplete prefix, otherwise the buffered JSON is flushed
        // prematurely and leaks to the client before execution.
        assertThat(parser.classify("{\"name\":\"get_claim_status\",", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
        assertThat(parser.classify("{\"name\":\"get_claim_status\",\"arguments\":", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
        assertThat(parser.classify("{\"name\":\"get_claim_status\",\"arguments\":{", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
    }

    @Test
    void classifyStillRejectsTrulyMalformedPrefix() {
        // A malformed object that cannot be extended into valid JSON must not be treated as
        // a prefix.
        assertThat(parser.classify("{\"name\": 123}", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }
}