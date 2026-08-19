package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QwenToolCallParserTest {

    private final QwenToolCallParser parser = new QwenToolCallParser();
    private static final Set<String> REGISTERED = Set.of("get_claim_status", "propose_claim_update");

    @Test
    void parsesValidToolCall() {
        Optional<QwenToolCall> result = parser.parse(
                "{\"name\":\"get_claim_status\",\"arguments\":{\"claimId\":42}}", REGISTERED);
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("get_claim_status");
        assertThat(((Number) result.get().arguments().get("claimId")).longValue()).isEqualTo(42L);
    }

    @Test
    void parsesNestedArguments() {
        Optional<QwenToolCall> result = parser.parse(
                "{\"name\":\"propose_claim_update\",\"arguments\":{\"note\":\"hello\",\"tags\":[\"a\",\"b\"]}}", REGISTERED);
        assertThat(result).isPresent();
        assertThat(result.get().arguments().get("tags")).isInstanceOf(java.util.List.class);
    }

    @Test
    void rejectsUnregisteredToolName() {
        assertThat(parser.parse("{\"name\":\"evil_tool\",\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsBlankAndNullContent() {
        assertThat(parser.parse(null, REGISTERED)).isEmpty();
        assertThat(parser.parse("", REGISTERED)).isEmpty();
        assertThat(parser.parse("  ", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsNonObjectJson() {
        assertThat(parser.parse("\"just a string\"", REGISTERED)).isEmpty();
        assertThat(parser.parse("[1,2,3]", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsMissingOrExtraFields() {
        assertThat(parser.parse("{\"name\":\"get_claim_status\"}", REGISTERED)).isEmpty();
        assertThat(parser.parse("{\"arguments\":{}}", REGISTERED)).isEmpty();
        assertThat(parser.parse("{\"name\":\"get_claim_status\",\"arguments\":{},\"extra\":1}", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsUnsafeToolNameWithSpecialChars() {
        assertThat(parser.parse("{\"name\":\"java.lang.Runtime\",\"arguments\":{}}", Set.of("java.lang.Runtime")))
                .isEmpty();
    }

    @Test
    void rejectsArgumentsThatAreNotObject() {
        assertThat(parser.parse("{\"name\":\"get_claim_status\",\"arguments\":\"nope\"}", REGISTERED)).isEmpty();
    }

    @Test
    void allowsEmptyArgumentsObject() {
        assertThat(parser.parse("{\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED)).isPresent();
    }

    @Test
    void rejectsOversizedContent() {
        QwenToolCallParser tiny = new QwenToolCallParser(10);
        assertThat(tiny.parse("{\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void classifiesCompleteToolCall() {
        assertThat(parser.classify("{\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.TOOL_CALL);
    }

    @Test
    void classifiesIncompletePrefixAsPossiblePrefix() {
        assertThat(parser.classify("{\"name\":\"get_claim", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
    }

    @Test
    void classifiesMalformedAsNotAToolCall() {
        assertThat(parser.classify("this is not json {", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void classifiesProseAsNotAToolCall() {
        assertThat(parser.classify("I will help you with your claim.", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void isCompleteToolCall() {
        assertThat(parser.isCompleteToolCall("{\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED))
                .isTrue();
        assertThat(parser.isCompleteToolCall("hello", REGISTERED)).isFalse();
    }

    @Test
    void classifiesBlankAsPossiblePrefix() {
        assertThat(parser.classify("", REGISTERED)).isEqualTo(QwenToolCallParser.Classification.POSSIBLE_PREFIX);
    }

    @Test
    void parsesWhenRegisteredNamesIsNull() {
        assertThat(parser.parse("{\"name\":\"get_claim_status\",\"arguments\":{}}", null)).isPresent();
    }

    @Test
    void parsesWhenRegisteredNamesIsEmpty() {
        assertThat(parser.parse("{\"name\":\"get_claim_status\",\"arguments\":{}}", Set.of())).isPresent();
    }

    @Test
    void rejectsNonTextualName() {
        assertThat(parser.parse("{\"name\":123,\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsNullNameValue() {
        assertThat(parser.parse("{\"name\":null,\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsDuplicateFieldsViaStrictDuplicateDetection() {
        assertThat(parser.parse(
                "{\"name\":\"get_claim_status\",\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED))
                .isEmpty();
    }

    @Test
    void convertsRichArgumentTypes() {
        Optional<QwenToolCall> result = parser.parse(
                "{\"name\":\"get_claim_status\",\"arguments\":{\"id\":1.5,\"flag\":true,\"nil\":null,"
                        + "\"nested\":{\"k\":\"v\"},\"items\":[1,\"two\"]}}", REGISTERED);
        assertThat(result).isPresent();
        Map<String, Object> args = result.get().arguments();
        assertThat(args.get("id")).isEqualTo(1.5d);
        assertThat(args.get("flag")).isEqualTo(true);
        assertThat(args).containsKey("nil");
        assertThat(args.get("nil")).isNull();
        assertThat(args.get("nested")).isEqualTo(Map.of("k", "v"));
        assertThat(args.get("items")).isInstanceOf(java.util.List.class);
    }

    @Test
    void rejectsControlCharactersInToolName() {
        assertThat(parser.parse("{\"name\":\"get\\nclaim\",\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void rejectsToolNameThatIsTooLong() {
        String longName = "a".repeat(129);
        assertThat(parser.parse("{\"name\":\"" + longName + "\",\"arguments\":{}}", REGISTERED)).isEmpty();
    }

    @Test
    void classifiesOversizedContentAsNotAToolCall() {
        QwenToolCallParser tiny = new QwenToolCallParser(5);
        assertThat(tiny.classify("{\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void classifiesCompleteInvalidJsonObjectAsNotAToolCall() {
        assertThat(parser.classify("{\"foo\":1}", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
        assertThat(parser.classify("[1,2]", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void classifyRejectsDuplicateFieldsAsNotAToolCall() {
        assertThat(parser.classify(
                "{\"name\":\"get_claim_status\",\"name\":\"get_claim_status\",\"arguments\":{}}", REGISTERED))
                .isEqualTo(QwenToolCallParser.Classification.NOT_A_TOOL_CALL);
    }

    @Test
    void isCompleteToolCallTreatsBlankAndPrefixAsIncomplete() {
        assertThat(parser.isCompleteToolCall("", REGISTERED)).isFalse();
        assertThat(parser.isCompleteToolCall("{\"name\":\"get_claim", REGISTERED)).isFalse();
    }
}
