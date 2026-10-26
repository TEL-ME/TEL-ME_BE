package com.telme.chat.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.code.CommonErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatExecutionTraceConverterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatExecutionTraceConverter converter = new ChatExecutionTraceConverter(mapper);

    @Test
    void responseKeepsExistingFieldsSnapshotsPrecisionAndSingleGenerationAlias() throws Exception {
        var execution = executionRow("""
                {"generationInputs":[{"consultRequestId":10,"sources":[{"score":0.876543}]}],
                 "guardResults":[{"outcome":"KEPT"}]}
                """);
        var routing = List.<Map<String, Object>>of(Map.of("intent", "FAQ", "refinedQuery", "정제 질문",
                "confidence", new BigDecimal("0.875"), "method", "LLM"));
        var attempts = List.of(attemptRow("{\"provider\":\"test-double\",\"options\":{\"temperature\":0.0}}"));

        JsonNode result = mapper.readTree(
                mapper.writeValueAsString(converter.toResponse(execution, routing, attempts)));
        assertThat(result).isEqualTo(mapper.readTree("""
                {"executionId":1,"sessionId":2,"inputMessageId":3,"outputMessageId":4,
                 "status":"COMPLETED","errorCode":null,"originalUserMessage":"원문 질문",
                 "finalAnswer":"최종 답변","answerStatus":"COMPLETED","answerBasis":"GROUNDED",
                 "traceRecorded":true,
                 "steps":{"generationInputs":[{"consultRequestId":10,"sources":[{"score":0.876543}]}],
                          "generationInput":{"consultRequestId":10,"sources":[{"score":0.876543}]},
                          "guardResults":[{"outcome":"KEPT"}]},
                 "routing":[{"intent":"FAQ","refinedQuery":"정제 질문","confidence":0.875,"method":"LLM"}],
                 "modelAttempts":[{"generationId":5,"taskType":"RAG_ANSWER","attempt":1,
                    "model":"test-double","promptVersion":"test-v1","contextCount":1,"status":"SUCCESS",
                    "firstTokenMs":null,"totalMs":20,
                    "configuration":{"provider":"test-double","options":{"temperature":0.0}}}]}
                """));
        assertThat(execution).containsKey("trace");
        assertThat(attempts.getFirst().get("configuration")).isInstanceOf(String.class);
    }

    @Test
    void unrecordedFailureKeepsNullFieldsEvenWhenMapperNormallyOmitsThem() throws Exception {
        var execution = executionRow(null);
        execution.put("status", "FAILED");
        execution.put("errorCode", "LLM504-0");
        execution.put("finalAnswer", null);
        execution.put("answerStatus", "FAILED");
        execution.put("answerBasis", null);
        ObjectMapper nonNullMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var response = converter.toResponse(execution, List.of(), List.of(attemptRow(null)));
        JsonNode result = nonNullMapper.valueToTree(response);

        assertThat(result.path("traceRecorded").asBoolean()).isFalse();
        assertThat(result.has("steps")).isTrue();
        assertThat(result.path("steps").isNull()).isTrue();
        assertThat(result.has("finalAnswer")).isTrue();
        assertThat(result.path("finalAnswer").isNull()).isTrue();
        assertThat(result.has("answerBasis")).isTrue();
        assertThat(result.path("answerBasis").isNull()).isTrue();
        assertThat(result.path("routing")).isEmpty();
        assertThat(result.at("/modelAttempts/0").has("configuration")).isTrue();
        assertThat(result.at("/modelAttempts/0/configuration").isNull()).isTrue();
    }

    @Test
    void multipleGenerationsKeepSeparateRequestsWithoutSingleGenerationAlias() {
        var response = converter.toResponse(executionRow("""
                {"generationInputs":[{"consultRequestId":10},{"consultRequestId":11}]}
                """), List.of(), List.of());

        assertThat(response.steps().path("generationInputs")).hasSize(2);
        assertThat(response.steps().has("generationInput")).isFalse();
        assertThat(response.steps().at("/generationInputs/0/consultRequestId").asLong()).isEqualTo(10);
        assertThat(response.steps().at("/generationInputs/1/consultRequestId").asLong()).isEqualTo(11);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unreadableStoredJsonUsesExistingSafeServerError(boolean invalidTrace) {
        var execution = executionRow(invalidTrace ? "PRIVATE_INVALID_JSON" : "{}");
        var attempts = List.of(attemptRow(invalidTrace ? "{}" : "PRIVATE_INVALID_OPTIONS"));

        assertThatThrownBy(() -> converter.toResponse(execution, List.of(), attempts))
                .isInstanceOfSatisfying(GeneralException.class,
                        failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .hasNoCause();
    }

    private Map<String, Object> executionRow(String trace) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("executionId", 1L);
        row.put("sessionId", 2L);
        row.put("inputMessageId", 3L);
        row.put("outputMessageId", 4L);
        row.put("status", "COMPLETED");
        row.put("errorCode", null);
        row.put("originalUserMessage", "원문 질문");
        row.put("finalAnswer", "최종 답변");
        row.put("answerStatus", "COMPLETED");
        row.put("answerBasis", "GROUNDED");
        row.put("trace", trace);
        return row;
    }

    private Map<String, Object> attemptRow(String configuration) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("generationId", 5L);
        row.put("taskType", "RAG_ANSWER");
        row.put("attempt", 1);
        row.put("model", "test-double");
        row.put("promptVersion", "test-v1");
        row.put("contextCount", 1);
        row.put("status", "SUCCESS");
        row.put("firstTokenMs", null);
        row.put("totalMs", 20);
        row.put("configuration", configuration);
        return row;
    }
}
