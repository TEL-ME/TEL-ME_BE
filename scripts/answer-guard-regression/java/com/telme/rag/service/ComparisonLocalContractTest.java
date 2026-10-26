package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.ComparisonCheckProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// A mock verdict checks the policy seam only. It is not an evaluation of real model accuracy.
class ComparisonLocalContractTest {
    static Stream<Arguments> cases() throws Exception {
        List<Arguments> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String name : List.of("cases.json", "recovery-safety.json")) {
            try (var input = ComparisonLocalContractTest.class
                    .getResourceAsStream("/answer-guard-recovery/" + name)) {
                for (JsonNode row : mapper.readTree(input)) {
                    result.add(Arguments.of(name + ":" + row.path("id").asText(), row));
                }
            }
        }
        return result.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void verifiesWiringWithExplicitMockVerdicts(String id, JsonNode row) throws Exception {
        String evidence = row.path("evidence").asText();
        String question = row.path("query").asText();
        String answer = row.path("answer").asText();
        boolean supported = row.path("category").asText().equals("VALID");
        ObjectMapper mapper = new ObjectMapper();
        LlmClient client = mock(LlmClient.class);
        when(client.generate(any())).thenAnswer(call -> {
            LlmRequest sent = call.getArgument(0);
            List<Map<String, Object>> decisions = new ArrayList<>();
            for (JsonNode candidate : mapper.readTree(sent.userPrompt()).path("sentences")) {
                decisions.add(Map.of("id", candidate.path("id").asInt(), "supported", supported,
                        "evidence", supported ? evidence : ""));
            }
            return mapper.writeValueAsString(Map.of("decisions", decisions));
        });
        ComparisonEvidenceJudge judge = new ComparisonEvidenceJudge(client, mapper,
                new ComparisonCheckProperties(true, 1000, 4, 12000), ExecutionTrace.noop());
        String output = new AnswerGuard().applyEvidencePolicy(answer, evidence, question, "",
                judge.newReview(null, null, question, evidence));
        assertThat(output).as(id).isEqualTo(supported ? answer.strip() : AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @Timeout(2)
    void boundsLargeInputWithoutCallingAnyModel() {
        for (int size : List.of(1000, 4000, 16000, 32000, 64000, 1000000)) {
            LlmClient client = mock(LlmClient.class);
            var judge = new ComparisonEvidenceJudge(client, new ObjectMapper(),
                    new ComparisonCheckProperties(true, 1000, 4, 12000), ExecutionTrace.noop());
            assertThat(judge.newReview(null, null, "질문", "근거" + " ".repeat(size + 12000))
                    .apply(List.of("비교 문장"))).isEmpty();
            org.mockito.Mockito.verifyNoInteractions(client);
        }
    }
}
