package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class FaqSearchAnswerProviderTest {

    @Test
    void searchesRefinedQueryAndPassesResultsToAnswerGeneration() {
        FaqSearchService searches = mock(FaqSearchService.class);
        var found =
                List.of(
                        new FaqSearchResponse(
                                7L,
                                "USIM",
                                "유심 재발급은 어디서 하나요?",
                                "가까운 매장에서 재발급할 수 있습니다.",
                                0.91,
                                1,
                                LocalDate.of(2026, 9, 21),
                                1));
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
        assertThat(request.getValue().query()).isEqualTo("유심 재발급 가능 매장");
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
