package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.rag.converter.AnswerContextConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class FaqSearchAnswerProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "청소년 요금제하고 시니어 요금제 차이가 뭐야",
            "청소년 요금제와 시니어 요금제 차이",
            "청소년 요금제와 시니어 요금제 가입 연령 비교"
    })
    void comparisonRequestAndSearchPhraseReachEvidenceValidationInsteadOfFreeFormGeneration(String question) {
        FaqSearchService searches = mock(FaqSearchService.class);
        LlmClient model = mock(LlmClient.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var youth = new FaqSearchResponse(3L, null, "test", "청소년 요금제 가입 연령",
                "청소년 요금제는 만 18세 이하만 가입할 수 있습니다.", 0.9, 1, null, 1, null);
        var senior = new FaqSearchResponse(4L, null, "test", "시니어 요금제 가입 연령",
                "시니어 요금제는 만 65세 이상만 가입할 수 있습니다.", 0.9, 1, null, 2, null);
        when(searches.search(any())).thenReturn(List.of(youth, senior));
        when(model.generate(any())).thenReturn("""
                {"answerable":true,"leftTarget":"청소년 요금제","rightTarget":"시니어 요금제",
                 "criterion":"차이","leftFaqId":3,
                 "leftQuote":"청소년 요금제는 만 18세 이하만 가입할 수 있습니다.","rightFaqId":4,
                 "rightQuote":"시니어 요금제는 만 65세 이상만 가입할 수 있습니다."}
                """);
        var provider = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(),
                new LlmComparisonEvidenceResolver(model, new ObjectMapper()));

        var result = provider.generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                question, "두 요금제 차이", Map.of()));

        assertThat(result.answer().content()).contains(youth.answer(), senior.answer());
        assertThat(result.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(result.sources()).extracting(source -> source.faqId()).containsExactly(3L, 4L);
        verify(model).generate(any());
        verifyNoInteractions(answers);
    }

    @Test
    void comparisonUsesOnlySourcesApprovedByResolver() {
        FaqSearchService searches = mock(FaqSearchService.class);
        ComparisonEvidenceResolver evidence = mock(ComparisonEvidenceResolver.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var source = new FaqSearchResponse(7L, null, "test", "비교", "A와 B가 다릅니다.",
                0.9, 1, null, 1, null);
        when(searches.search(any())).thenReturn(List.of(source));
        when(evidence.applies("A와 B를 비교해줘")).thenReturn(true);
        when(evidence.resolveDetailed(any(), any(), any(), any(), any()))
                .thenReturn(new ComparisonEvidenceResolver.Resolution(List.of(), null));
        var expected = GeneratedAnswer.withoutSources(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "안내드릴 수 있는 정보가 없습니다.",
                ChatMessage.AnswerBasis.NO_EVIDENCE, List.of(), null));
        when(answers.generate(any(), any())).thenReturn(expected);

        var result = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(), evidence)
                .generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                        "A와 B를 비교해줘", "A B 비교", Map.of()));

        assertThat(result).isSameAs(expected);
        verify(answers).generate(any(), org.mockito.ArgumentMatchers.eq(List.of()));
    }

    @Test
    void comparisonReturnsOnlyVerifiedFaqQuotesWithoutFreeFormGeneration() {
        FaqSearchService searches = mock(FaqSearchService.class);
        ComparisonEvidenceResolver evidence = mock(ComparisonEvidenceResolver.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var source = new FaqSearchResponse(65L, null, "test", "명의변경 서류",
                "양도인과 양수인의 신분증이 각각 필요합니다.", 0.9, 1, null, 1, null);
        when(searches.search(any())).thenReturn(List.of(source));
        when(evidence.applies("명의 변경과 번호 이동 서류 비교해줘")).thenReturn(true);
        when(evidence.resolveDetailed(any(), any(), any(), any(), any()))
                .thenReturn(new ComparisonEvidenceResolver.Resolution(List.of(source),
                        "명의 변경: 양도인과 양수인의 신분증이 각각 필요합니다."));

        var result = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(), evidence)
                .generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                        "명의 변경과 번호 이동 서류 비교해줘", "명의 변경 번호 이동", Map.of()));

        assertThat(result.answer().content()).contains("양도인과 양수인의 신분증");
        assertThat(result.answer().followUps()).isEmpty();
        assertThat(result.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(result.sources()).extracting(sourceRecord -> sourceRecord.faqId()).containsExactly(65L);
        verifyNoInteractions(answers);
    }

    // 비교한 FAQ마다 추천 질문을 하나씩 붙이고, 겹치면 그 FAQ의 다음 추천을 쓴다
    @Test
    void comparisonAnswerGetsOneSuggestedQuestionPerComparedFaq() {
        FaqSearchService searches = mock(FaqSearchService.class);
        ComparisonEvidenceResolver evidence = mock(ComparisonEvidenceResolver.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var suggestions = mock(RagSearchResultAnswerGenerator.SuggestedQuestions.class);
        var nameChange = new FaqSearchResponse(65L, null, "test", "명의변경 서류",
                "양도인과 양수인의 신분증이 각각 필요합니다.", 0.9, 1, null, 1, null);
        var porting = new FaqSearchResponse(66L, null, "test", "번호이동 서류",
                "본인 신분증이 필요합니다.", 0.9, 1, null, 2, null);
        when(searches.search(any())).thenReturn(List.of(nameChange, porting));
        when(evidence.applies(any())).thenReturn(true);
        when(evidence.resolveDetailed(any(), any(), any(), any(), any()))
                .thenReturn(new ComparisonEvidenceResolver.Resolution(List.of(nameChange, porting),
                        "명의 변경: 양도인과 양수인의 신분증이 각각 필요합니다.\n번호 이동: 본인 신분증이 필요합니다."));
        when(suggestions.suggest(ChatMessage.AnswerBasis.GROUNDED, List.of(nameChange)))
                .thenReturn(List.of("가까운 매장을 알려주세요.", "명의변경 수수료가 있나요?"));
        when(suggestions.suggest(ChatMessage.AnswerBasis.GROUNDED, List.of(porting)))
                .thenReturn(List.of("가까운 매장을 알려주세요.", "번호이동 수수료가 있나요?"));
        var provider = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(), evidence,
                FaqCandidateEvidenceResolver.disabled(), new AnswerContextConverter(), suggestions);

        var result = provider.generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                "명의 변경과 번호 이동 서류 비교해줘", "명의 변경 번호 이동", Map.of()));

        assertThat(result.answer().followUps())
                .containsExactly("가까운 매장을 알려주세요.", "번호이동 수수료가 있나요?");
        verifyNoInteractions(answers);
    }

    @Test
    void compoundFaqRecoversOnlyVerifiedCandidateWhenThresholdSearchIsEmpty() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var candidateEvidence = mock(FaqCandidateEvidenceResolver.class);
        var source = new FaqSearchResponse(65L, null, "test", "명의변경 서류",
                "양도인과 양수인의 신분증이 각각 필요합니다.", 0.68, 1, null, 3, null);
        when(searches.search(any())).thenReturn(List.of());
        when(searches.searchCandidates(any())).thenReturn(List.of(source));
        when(candidateEvidence.resolve(any(), any(), any(), any())).thenReturn(source);
        var provider = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(),
                ComparisonEvidenceResolver.passthrough(), candidateEvidence);

        var result = provider.generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                "명의 변경에 필요한 서류는 무엇인가요?", "명의 변경에 필요한 서류는 무엇인가요?",
                Map.of(), false));

        assertThat(result.answer().content()).isEqualTo(source.answer());
        assertThat(result.answer().followUps()).isEmpty();
        assertThat(result.sources()).extracting(found -> found.faqId()).containsExactly(65L);
        verifyNoInteractions(answers);
    }

    // 후보 확인으로 답한 경우도 일반 답변과 같이 근거 FAQ로 추천 질문을 붙인다
    @Test
    void verifiedCandidateAnswerGetsSuggestedQuestionsOfThatFaq() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        var candidateEvidence = mock(FaqCandidateEvidenceResolver.class);
        var suggestions = mock(RagSearchResultAnswerGenerator.SuggestedQuestions.class);
        var other = new FaqSearchResponse(64L, null, "test", "번호이동 서류",
                "본인 신분증이 필요합니다.", 0.70, 1, null, 2, null);
        var source = new FaqSearchResponse(65L, null, "test", "명의변경 서류",
                "양도인과 양수인의 신분증이 각각 필요합니다.", 0.68, 1, null, 3, null);
        when(searches.search(any())).thenReturn(List.of());
        when(searches.searchCandidates(any())).thenReturn(List.of(other, source));
        when(candidateEvidence.resolve(any(), any(), any(), any())).thenReturn(source);
        when(suggestions.suggest(ChatMessage.AnswerBasis.GROUNDED, List.of(source)))
                .thenReturn(List.of("명의변경 수수료가 있나요?"));
        var provider = new FaqSearchAnswerProvider(searches, answers, ExecutionTrace.noop(),
                ComparisonEvidenceResolver.passthrough(), candidateEvidence, new AnswerContextConverter(),
                suggestions);

        var result = provider.generate(new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ,
                "명의 변경에 필요한 서류는 무엇인가요?", "명의 변경에 필요한 서류는 무엇인가요?",
                Map.of(), false));

        assertThat(result.answer().followUps()).containsExactly("명의변경 수수료가 있나요?");
        verify(suggestions).suggest(ChatMessage.AnswerBasis.GROUNDED, List.of(source));
        verifyNoInteractions(answers);
    }

    @Test
    void failedSearchRetainsCauseAndDoesNotGenerateFromEmptyEvidence() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        RuntimeException failure = new IllegalStateException("검색 시스템 원인");
        when(searches.search(any())).thenThrow(failure);
        var provider = new FaqSearchAnswerProvider(searches, answers);

        assertThatThrownBy(() -> provider.generate(new AnswerInput(
                1L, 2L, 3L, Purpose.GENERAL_FAQ, "질문", "질문", Map.of())))
                .isInstanceOf(FaqAnswerSearchException.class).hasCause(failure);
        verifyNoInteractions(answers);
    }

    @Test
    void searchCancellationRetainsCancellationClassification() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        RuntimeException cancellation = new LlmStreamCancelledException();
        when(searches.search(any())).thenThrow(cancellation);
        var provider = new FaqSearchAnswerProvider(searches, answers);

        assertThatThrownBy(() -> provider.generate(new AnswerInput(
                1L, 2L, 3L, Purpose.GENERAL_FAQ, "질문", "질문", Map.of())))
                .isSameAs(cancellation);
        verifyNoInteractions(answers);
    }

    @Test
    void searchesOriginalQueryAndPassesResultsToAnswerGeneration() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found =
                List.of(
                        new FaqSearchResponse(
                                7L,
                                null,
                                "USIM",
                                "유심 재발급은 어디서 하나요?",
                                "가까운 매장에서 재발급할 수 있습니다.",
                                0.91,
                                1,
                                LocalDate.of(2026, 9, 21),
                                1, null));
        when(searches.search(any())).thenReturn(found);
        AtomicReference<List<FaqSearchResponse>> received = new AtomicReference<>();
        ChatAnswer expected =
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        "가까운 매장에서 재발급할 수 있습니다.",
                        ChatMessage.AnswerBasis.GROUNDED,
                        List.of(),
                        null);
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        (input, results) -> {
                            received.set(results);
                            return GeneratedAnswer.withoutSources(expected);
                        });
        var input =
                new AnswerInput(
                        10L,
                        20L,
                        30L,
                        Purpose.GENERAL_FAQ,
                        "유심 재발급은 어디서 하나요?",
                        "유심 재발급 가능 매장",
                        Map.of("location", "강남역"));

        GeneratedAnswer answer = provider.generate(input);

        var request = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(searches).search(request.capture());
        assertThat(request.getValue().query()).isEqualTo("유심 재발급은 어디서 하나요?");
        assertThat(request.getValue().topK()).isEqualTo(3);
        assertThat(received.get()).containsExactlyElementsOf(found);
        assertThat(received.get()).isNotEmpty();
        assertThat(answer.answer()).isSameAs(expected);
    }

    @Test
    void emptySearchResultStillReachesNoEvidenceGeneration() {
        FaqSearchService searches = mock(FaqSearchService.class);
        when(searches.search(any())).thenReturn(List.of());
        AtomicReference<List<FaqSearchResponse>> received = new AtomicReference<>();
        ChatAnswer noEvidence =
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        "안내드릴 수 있는 정보가 없습니다.",
                        ChatMessage.AnswerBasis.NO_EVIDENCE,
                        List.of(),
                        null);
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        (input, results) -> {
                            received.set(results);
                            return GeneratedAnswer.withoutSources(noEvidence);
                        });

        GeneratedAnswer answer =
                provider.generate(
                        new AnswerInput(
                                1L,
                                2L,
                                3L,
                                Purpose.GENERAL_FAQ,
                                "가입 가능한 상품이 있나요?",
                                "없는 질문",
                                Map.of()));

        assertThat(received.get()).isEmpty();
        assertThat(answer.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
    }

    @Test
    void recordsNoSearchResultsGuardOnceOnlyWhenSearchIsEmpty() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found = new FaqSearchResponse(
                7L, null, "PLAN", "요금제 변경 횟수는?", "월 1회 변경할 수 있습니다.",
                0.80, 1, LocalDate.of(2026, 9, 21), 1, null);
        List<String> stages = new java.util.ArrayList<>();
        ExecutionTrace trace = new ExecutionTrace() {
            @Override
            public void stage(Long executionId, String stage, Object value) {
                stages.add(stage + ":" + value);
            }

            @Override
            public void append(Long executionId, String stage, Object value) {}
        };
        var provider = new FaqSearchAnswerProvider(searches, (input, results) ->
                GeneratedAnswer.withoutSources(new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, "답변", null, List.of(), null)), trace);
        var input = new AnswerInput(1L, 2L, 3L, Purpose.GENERAL_FAQ, "질문", "질문", Map.of());

        when(searches.search(any())).thenReturn(List.of(found));
        provider.generate(input);
        assertThat(stages).isEmpty();

        when(searches.search(any())).thenReturn(List.of());
        provider.generate(input);
        assertThat(stages).hasSize(1);
        assertThat(stages.getFirst()).startsWith("guard:").contains("NO_SEARCH_RESULTS");
    }

    @Test
    void searchesRefinedQueryWhenOriginalSearchHasNoEvidence() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found = new FaqSearchResponse(
                7L, null, "PLAN", "요금제 변경 횟수는?", "월 1회 변경할 수 있습니다.",
                0.80, 1, LocalDate.of(2026, 9, 21), 1, null);
        when(searches.search(any())).thenAnswer(invocation -> {
            FaqSearchRequest request = invocation.getArgument(0);
            return request.query().equals("요금제 변경 횟수") ? List.of(found) : List.of();
        });
        AtomicReference<List<FaqSearchResponse>> received = new AtomicReference<>();
        var provider = new FaqSearchAnswerProvider(searches, (input, results) -> {
            received.set(results);
            return GeneratedAnswer.withoutSources(new ChatAnswer(
                    ChatMessage.MessageType.ANSWER, "월 1회 변경할 수 있습니다.",
                    ChatMessage.AnswerBasis.GROUNDED, List.of(), null));
        });

        provider.generate(new AnswerInput(
                1L, 2L, 3L, Purpose.GENERAL_FAQ,
                "요금제는 한 달에 몇 번까지 바꿀 수 있나요?", "요금제 변경 횟수", Map.of()));

        var requests = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(searches, times(2)).search(requests.capture());
        assertThat(requests.getAllValues()).extracting(FaqSearchRequest::query)
                .containsExactly("요금제는 한 달에 몇 번까지 바꿀 수 있나요?", "요금제 변경 횟수");
        assertThat(received.get()).containsExactly(found);
    }

    @Test
    void doesNotRepeatSearchWhenOriginalQuestionIsAlreadyTheSearchQuery() {
        FaqSearchService searches = mock(FaqSearchService.class);
        when(searches.search(any())).thenReturn(List.of());
        var provider = new FaqSearchAnswerProvider(searches, (input, results) ->
                GeneratedAnswer.withoutSources(new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, "안내드릴 수 있는 정보가 없습니다.",
                        ChatMessage.AnswerBasis.NO_EVIDENCE, List.of(), null)));

        provider.generate(new AnswerInput(
                1L, 2L, 3L, Purpose.GENERAL_FAQ,
                "요금제는 한 달에 몇 번까지 바꿀 수 있나요?",
                "요금제는 한 달에 몇 번까지 바꿀 수 있나요?", Map.of()));

        verify(searches, times(1)).search(any());
    }

    @Test
    void storePurposeIsNotSentToFaqSearch() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        (input, results) ->
                                GeneratedAnswer.withoutSources(
                                        new ChatAnswer(
                                                ChatMessage.MessageType.ANSWER,
                                                "답변",
                                                null,
                                                List.of(),
                                                null)));

        assertThatThrownBy(
                        () ->
                                provider.generate(
                                        new AnswerInput(
                                                1L,
                                                2L,
                                                3L,
                                                Purpose.NEARBY_STORE,
                                                "가까운 매장",
                                                "가까운 매장",
                                                Map.of("location", "강남"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일반 FAQ");

        verifyNoInteractions(searches);
    }

}
