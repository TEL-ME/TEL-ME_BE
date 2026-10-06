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
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.exception.LlmStreamCancelledException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class FaqSearchAnswerProviderTest {

    @Test
    @DisplayName("검색 실패의 원인을 보존한다")
    void 검색_실패의_원인을_보존한다() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        RuntimeException failure = new IllegalStateException("검색 시스템 원인");
        when(searches.search(any())).thenThrow(failure);
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        answers,
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

        assertThatThrownBy(
                        () ->
                                provider.generate(
                                        new AnswerInput(
                                                1L,
                                                2L,
                                                3L,
                                                Purpose.GENERAL_FAQ,
                                                "질문",
                                                "질문",
                                                Map.of())))
                .isInstanceOf(FaqAnswerSearchException.class)
                .hasCause(failure);
        verifyNoInteractions(answers);
    }

    @Test
    @DisplayName("검색 취소 분류를 보존한다")
    void 검색_취소_분류를_보존한다() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var answers = mock(FaqSearchAnswerProvider.SearchResultAnswerGenerator.class);
        RuntimeException cancellation = new LlmStreamCancelledException();
        when(searches.search(any())).thenThrow(cancellation);
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        answers,
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

        assertThatThrownBy(
                        () ->
                                provider.generate(
                                        new AnswerInput(
                                                1L,
                                                2L,
                                                3L,
                                                Purpose.GENERAL_FAQ,
                                                "질문",
                                                "질문",
                                                Map.of())))
                .isSameAs(cancellation);
        verifyNoInteractions(answers);
    }

    @Test
    @DisplayName("원문 검색 근거를 생성에 전달한다")
    void 원문_검색_근거를_생성에_전달한다() {
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
                                1,
                                null));
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
                        },
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());
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
    @DisplayName("빈 검색도 근거 부족 경로로 전달한다")
    void 빈_검색도_근거_부족_경로로_전달한다() {
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
                        },
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

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
    @DisplayName("빈 검색의 Guard 기록을 한 번만 남긴다")
    void 빈_검색의_Guard_기록을_한_번만_남긴다() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found =
                new FaqSearchResponse(
                        7L,
                        null,
                        "PLAN",
                        "요금제 변경 횟수는?",
                        "월 1회 변경할 수 있습니다.",
                        0.80,
                        1,
                        LocalDate.of(2026, 9, 21),
                        1,
                        null);
        List<String> stages = new java.util.ArrayList<>();
        ExecutionTrace trace =
                new ExecutionTrace() {
                    @Override
                    public void stage(Long executionId, String stage, Object value) {
                        stages.add(stage + ":" + value);
                    }

                    @Override
                    public void append(Long executionId, String stage, Object value) {}
                };
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
                                                null)),
                        trace,
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());
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
    @DisplayName("원문 근거가 없으면 재작성 질문으로 검색한다")
    void 원문_근거가_없으면_재작성_질문으로_검색한다() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found =
                new FaqSearchResponse(
                        7L,
                        null,
                        "PLAN",
                        "요금제 변경 횟수는?",
                        "월 1회 변경할 수 있습니다.",
                        0.80,
                        1,
                        LocalDate.of(2026, 9, 21),
                        1,
                        null);
        when(searches.search(any()))
                .thenAnswer(
                        invocation -> {
                            FaqSearchRequest request = invocation.getArgument(0);
                            return request.query().equals("요금제 변경 횟수") ? List.of(found) : List.of();
                        });
        AtomicReference<List<FaqSearchResponse>> received = new AtomicReference<>();
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        (input, results) -> {
                            received.set(results);
                            return GeneratedAnswer.withoutSources(
                                    new ChatAnswer(
                                            ChatMessage.MessageType.ANSWER,
                                            "월 1회 변경할 수 있습니다.",
                                            ChatMessage.AnswerBasis.GROUNDED,
                                            List.of(),
                                            null));
                        },
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

        provider.generate(
                new AnswerInput(
                        1L,
                        2L,
                        3L,
                        Purpose.GENERAL_FAQ,
                        "요금제는 한 달에 몇 번까지 바꿀 수 있나요?",
                        "요금제 변경 횟수",
                        Map.of()));

        var requests = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(searches, times(2)).search(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(FaqSearchRequest::query)
                .containsExactly("요금제는 한 달에 몇 번까지 바꿀 수 있나요?", "요금제 변경 횟수");
        assertThat(received.get()).containsExactly(found);
    }

    @Test
    @DisplayName("같은 질문은 검색을 반복하지 않는다")
    void 같은_질문은_검색을_반복하지_않는다() {
        FaqSearchService searches = mock(FaqSearchService.class);
        when(searches.search(any())).thenReturn(List.of());
        var provider =
                new FaqSearchAnswerProvider(
                        searches,
                        (input, results) ->
                                GeneratedAnswer.withoutSources(
                                        new ChatAnswer(
                                                ChatMessage.MessageType.ANSWER,
                                                "안내드릴 수 있는 정보가 없습니다.",
                                                ChatMessage.AnswerBasis.NO_EVIDENCE,
                                                List.of(),
                                                null)),
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

        provider.generate(
                new AnswerInput(
                        1L,
                        2L,
                        3L,
                        Purpose.GENERAL_FAQ,
                        "요금제는 한 달에 몇 번까지 바꿀 수 있나요?",
                        "요금제는 한 달에 몇 번까지 바꿀 수 있나요?",
                        Map.of()));

        verify(searches, times(1)).search(any());
    }

    @Test
    @DisplayName("매장 질문은 FAQ 검색으로 보내지 않는다")
    void 매장_질문은_FAQ_검색으로_보내지_않는다() {
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
                                                null)),
                        ExecutionTrace.noop(),
                        new DialogueService(prompt -> "안내"),
                        new ConfirmedConditionConverter());

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
