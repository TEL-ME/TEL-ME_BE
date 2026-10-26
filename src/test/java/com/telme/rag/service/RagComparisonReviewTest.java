package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.config.ComparisonCheckProperties;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.dto.req.AnswerRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagComparisonReviewTest {

    private static final String EVIDENCE = AnswerGuardRecoveryBehaviorTest.EVIDENCE;
    private static final String ANSWER = "39,000원인 7일 기간권이 가장 저렴해요.";
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient generation = mock(LlmClient.class);
    private final LlmClient judgement = mock(LlmClient.class);
    private final LlmClient retry = mock(LlmClient.class);
    private final EvidenceRelevanceChecker relevance = mock(EvidenceRelevanceChecker.class);
    private final List<String> delivered = new ArrayList<>();
    private final List<LlmRequest> requests = new ArrayList<>();

    @Test
    void deliversOnlyTheValidatedAnswerAndUsesOneAdditionalModelCall() throws Exception {
        scripted(generation, ANSWER);
        approve();
        var result = generator(null).generate(request(EVIDENCE), handler());
        assertThat(result.answer()).isEqualTo(ANSWER);
        assertThat(delivered).containsExactly(ANSWER);
        verify(generation).stream(any(), any());
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(judgement).generate(request.capture());
        assertThat(request.getValue().executionId()).isEqualTo(7L);
        assertThat(mapper.readTree(request.getValue().userPrompt()).path("evidence").asText()).isEqualTo(EVIDENCE);
        assertThat(request.getValue().userPrompt()).doesNotContain("틀린 Q의 99,999원");
    }

    @Test
    void ordinaryAnswersRequireNoAdditionalModelCall() {
        scripted(generation, "7일 기간권은 39,000원입니다.");
        assertThat(generator(null).generate(request(EVIDENCE), handler()).answer())
                .isEqualTo("7일 기간권은 39,000원입니다.");
        verify(judgement, never()).generate(any());
    }

    @Test
    void failedJudgementCompletesWithSafeFallbackAndDoesNotRetryTheModel() {
        scripted(generation, ANSWER);
        when(judgement.generate(any())).thenThrow(new IllegalStateException("timeout"));
        assertThat(generator(null).generate(request(EVIDENCE), handler()).answer())
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(delivered).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(judgement).generate(any());
        verify(retry, never()).stream(any(), any());
    }

    @Test
    void rejectsUnsupportedSentencesWithinASingleBatch() throws Exception {
        scripted(generation, ANSWER + " 기간권은 다른 상품보다 더 빠릅니다.");
        when(judgement.generate(any())).thenReturn(mapper.writeValueAsString(Map.of("decisions", List.of(
                Map.of("id", 0, "supported", true, "evidence", EVIDENCE),
                Map.of("id", 1, "supported", false, "evidence", "")))));
        assertThat(generator(null).generate(request(EVIDENCE), handler()).answer()).isEqualTo(ANSWER);
        verify(judgement).generate(any());
    }

    @Test
    void existingAmountRetryReusesOnlyAnIdenticalApprovedSentenceWithoutCallingJudgeAgain() throws Exception {
        scripted(generation, ANSWER + " 배송비는 8,800원입니다.");
        scripted(retry, ANSWER);
        approve();
        assertThat(generator(retry).generate(request(EVIDENCE), handler()).answer()).isEqualTo(ANSWER);
        verify(judgement).generate(any());
        verify(retry).stream(any(), any());
        assertThat(delivered).containsExactly(ANSWER);
    }

    @Test
    void aRetryCannotReuseApprovalForANewComparisonSentence() throws Exception {
        scripted(generation, ANSWER + " 배송비는 8,800원입니다.");
        scripted(retry, "7일 기간권이 39,000원으로 가장 저렴합니다.");
        approve();
        assertThat(generator(retry).generate(request(EVIDENCE), handler()).answer())
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        verify(judgement).generate(any());
    }

    @Test
    void cancellationDuringJudgementIsNotConvertedIntoSuccessfulFallback() {
        scripted(generation, ANSWER);
        when(judgement.generate(any())).thenThrow(new LlmStreamCancelledException());
        assertThatThrownBy(() -> generator(null).generate(request(EVIDENCE), handler()))
                .isInstanceOf(LlmStreamCancelledException.class);
        assertThat(delivered).isEmpty();
    }

    private RagAnswerGenerator generator(LlmClient amountRetry) {
        when(relevance.canAnswer(any(), any(), any())).thenReturn(true);
        var judge = new ComparisonEvidenceJudge(judgement, mapper,
                new ComparisonCheckProperties(true, 1000, 4, 12000), ExecutionTrace.noop());
        return new RagAnswerGenerator(generation, new AnswerContextConverter(), new AnswerGuard(), relevance,
                mock(LlmGenerationRecorder.class), ExecutionTrace.noop(), amountRetry, judge);
    }

    private AnswerRequest request(String evidence) {
        var faq = new FaqSearchResponse(177L, "ROAMING-0066", "ROAMING", "틀린 Q의 99,999원", evidence,
                1.0, 1, null, 1, null);
        return AnswerRequest.builder().executionId(7L).consultRequestId(3L)
                .userQuery("7일 동안 일본 가요. 로밍 어떤 게 저렴해요?")
                .searchResults(List.of(faq)).build();
    }

    private void scripted(LlmClient client, String answer) {
        doAnswer(call -> {
            requests.add(call.getArgument(0));
            LlmStreamHandler target = call.getArgument(1);
            target.onToken(answer);
            assertThat(delivered).isEmpty();
            target.onComplete();
            return null;
        }).when(client).stream(any(), any());
    }

    private void approve() throws Exception {
        when(judgement.generate(any())).thenReturn(mapper.writeValueAsString(Map.of("decisions", List.of(
                Map.of("id", 0, "supported", true, "evidence", EVIDENCE)))));
    }

    private LlmStreamHandler handler() {
        return new LlmStreamHandler() {
            @Override
            public void onToken(String text) {
                delivered.add(text);
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        };
    }
}
