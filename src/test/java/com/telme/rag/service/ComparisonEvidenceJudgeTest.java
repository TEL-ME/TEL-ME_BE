package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.rag.config.ComparisonCheckProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ComparisonEvidenceJudgeTest {

    private static final String EVIDENCE = "7일 기간권이 같은 기간의 다른 상품보다 저렴합니다.";
    private static final String ANSWER = "기간권이 제일 저렴해요.";
    private final LlmClient client = mock(LlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComparisonCheckProperties properties = new ComparisonCheckProperties(true, 1000, 4, 12000);

    @Test
    void usesQuestionAndFullAnswerEvidenceWithoutAnAnswerGrammarAndCallsAtMostOnce() throws Exception {
        when(client.generate(any())).thenReturn(approved(EVIDENCE));
        var review = judge(properties).newReview(7L, 3L, "일주일 해외여행 가는데 뭐가 제일 싸요?", EVIDENCE);
        assertThat(review.apply(List.of(ANSWER))).containsExactly(ANSWER);
        assertThat(review.apply(List.of("다른 답이 제일 저렴해요."))).isEmpty();
        ArgumentCaptor<LlmRequest> sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).generate(sent.capture());
        assertThat(sent.getValue().executionId()).isEqualTo(7L);
        assertThat(sent.getValue().consultRequestId()).isEqualTo(3L);
        assertThat(sent.getValue().taskType()).isEqualTo(TaskType.EVIDENCE_CHECK);
        assertThat(sent.getValue().promptVersion()).isEqualTo("comparison-grounding-v1");
        assertThat(sent.getValue().maxTokens()).isEqualTo(1000);
        var input = mapper.readTree(sent.getValue().userPrompt());
        assertThat(input.path("evidence").asText()).isEqualTo(EVIDENCE);
        assertThat(input.path("sentences").get(0).path("text").asText()).isEqualTo(ANSWER);
    }

    @Test
    void batchesAndKeepsOnlyExplicitlySupportedSentences() {
        when(client.generate(any())).thenReturn("{\"decisions\":["
                + "{\"id\":1,\"supported\":false,\"evidence\":\"\"},"
                + "{\"id\":0,\"supported\":true,\"evidence\":\"" + EVIDENCE + "\"}]}");
        assertThat(judge(properties).newReview(null, null, "질문", EVIDENCE)
                .apply(List.of(ANSWER, "모든 상품보다 빠릅니다."))).containsExactly(ANSWER);
        verify(client).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "null", "{}", "{\"decisions\":[]}", "{\"decisions\":null}",
            "{\"decisions\":[{\"id\":0,\"supported\":\"true\",\"evidence\":\"근거\"}]}",
            "{\"decisions\":[{\"id\":0,\"supported\":true,\"evidence\":\"없는 인용\"}]}",
            "{\"decisions\":[{\"id\":0,\"supported\":true,\"evidence\":\"\"}]}",
            "{\"decisions\":[{\"id\":0,\"supported\":true}]}",
            "{\"decisions\":[{\"id\":-1,\"supported\":true,\"evidence\":\"근거\"}]}",
            "{\"decisions\":[{\"id\":1,\"supported\":false,\"evidence\":\"\"}]}",
            "{\"decisions\":[{\"id\":0.0,\"supported\":false,\"evidence\":\"\"}]}",
            "{\"decisions\":[{\"id\":0,\"supported\":false,\"evidence\":\"\"}]} trailing",
            "{\"decisions\":[{\"id\":0,\"supported\":true,\"supported\":false,\"evidence\":\"\"}]}",
            "```json\n{\"decisions\":[]}\n```"
    })
    void invalidOrUncertainResponsesNeverApprove(String response) {
        when(client.generate(any())).thenReturn(response);
        assertThat(judge(properties).newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
    }

    @Test
    void rejectsDuplicatedIdsRatherThanApprovingAnUnansweredSentence() {
        when(client.generate(any())).thenReturn("{\"decisions\":["
                + "{\"id\":0,\"supported\":true,\"evidence\":\"" + EVIDENCE + "\"},"
                + "{\"id\":0,\"supported\":false,\"evidence\":\"\"}]}");
        assertThat(judge(properties).newReview(null, null, "질문", EVIDENCE)
                .apply(List.of(ANSWER, "다른 문장"))).isEmpty();
    }

    @Test
    void rejectsFailureAndNullResponseButPropagatesCancellation() {
        when(client.generate(any())).thenThrow(new IllegalStateException("timeout"));
        assertThat(judge(properties).newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
        org.mockito.Mockito.doReturn(null).when(client).generate(any());
        assertThat(judge(properties).newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
        org.mockito.Mockito.doThrow(new LlmStreamCancelledException()).when(client).generate(any());
        assertThatThrownBy(() -> judge(properties).newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER)))
                .isInstanceOf(LlmStreamCancelledException.class);
    }

    @Test
    void disabledUnavailableAndOverLimitNeverCallAModel() {
        assertThat(judge(new ComparisonCheckProperties(false, 1000, 4, 12000))
                .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
        assertThat(judge(new ComparisonCheckProperties(true, 1000, 1, 12000))
                .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER, "다른 문장"))).isEmpty();
        assertThat(judge(new ComparisonCheckProperties(true, 1000, 4, 1))
                .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
        var unavailable = new ComparisonEvidenceJudge((LlmClient) null, mapper, properties, ExecutionTrace.noop());
        assertThat(unavailable.newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty();
        verify(client, never()).generate(any());
    }

    @Test
    void springUsesOnlyTheNonRetryEvidenceJudgeBean() throws Exception {
        LlmClient answerClient = mock(LlmClient.class);
        when(client.generate(any())).thenReturn(approved(EVIDENCE));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(ComparisonEvidenceJudge.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ExecutionTrace.class, ExecutionTrace::noop)
                .withPropertyValues("rag.comparison-check.enabled=true")
                .withBean("answerClient", LlmClient.class, () -> answerClient)
                .withBean(com.telme.llm.config.BedrockConfig.EVIDENCE_JUDGE_LLM_CLIENT,
                        LlmClient.class, () -> client)
                .run(context -> assertThat(context.getBean(ComparisonEvidenceJudge.class)
                        .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).containsExactly(ANSWER));
        verify(client).generate(any());
        verify(answerClient, never()).generate(any());
    }

    @Test
    void springCanStartWithoutABedrockClientAndTheFlagCanDisableTheFeature() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(ComparisonEvidenceJudge.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ExecutionTrace.class, ExecutionTrace::noop)
                .run(context -> assertThat(context.getBean(ComparisonEvidenceJudge.class)
                        .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty());
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(ComparisonEvidenceJudge.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ExecutionTrace.class, ExecutionTrace::noop)
                .withBean(com.telme.llm.config.BedrockConfig.EVIDENCE_JUDGE_LLM_CLIENT, LlmClient.class, () -> client)
                .withPropertyValues("rag.comparison-check.enabled=false")
                .run(context -> assertThat(context.getBean(ComparisonEvidenceJudge.class)
                        .newReview(null, null, "질문", EVIDENCE).apply(List.of(ANSWER))).isEmpty());
        verify(client, never()).generate(any());
    }

    private ComparisonEvidenceJudge judge(ComparisonCheckProperties config) {
        return new ComparisonEvidenceJudge(client, mapper, config, ExecutionTrace.noop());
    }

    private String approved(String quote) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("decisions", List.of(
                java.util.Map.of("id", 0, "supported", true, "evidence", quote))));
    }
}
