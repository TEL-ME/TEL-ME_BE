package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.service.AnswerGenerator;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class RagSearchResultAnswerGeneratorTest {
    private final AnswerGenerator answers = mock(AnswerGenerator.class);
    private final LlmStreamHandler handler = mock(LlmStreamHandler.class);

    @Test
    void passesOriginalQueryConfirmedConditionsAndSearchResultsToRag() {
        var source =
                new AnswerSource(
                        9L,
                        "매장",
                        2,
                        LocalDate.of(2026, 9, 1),
                        (short) 1,
                        BigDecimal.valueOf(0.91));
        when(answers.generate(any(), any()))
                .thenAnswer(
                        invocation -> {
                            LlmStreamHandler stream = invocation.getArgument(1);
                            stream.onToken("강남역 ");
                            stream.onRetry(1, new IllegalStateException("재시도"));
                            stream.onComplete();
                            stream.onError(new IllegalStateException("모델 오류"));
                            return AnswerResult.builder()
                                    .answer("강남역 인근 매장을 안내해 드릴게요.")
                                    .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                                    .sources(List.of(source))
                                    .build();
                        });
        var generator =
                new RagSearchResultAnswerGenerator(answers, executionId -> handler);
        var input =
                new AnswerInput(
                        31L,
                        7L,
                        11L,
                        Purpose.GENERAL_FAQ,
                        "강남역 근처 매장을 알려줘",
                        "강남역 인근 매장",
                        Map.of("location", "강남역"));
        var searchResults =
                List.of(
                        new FaqSearchResponse(
                                9L,
                                "매장",
                                "강남역 인근 매장은 어디인가요?",
                                "강남역 주변 매장을 안내합니다.",
                                0.91,
                                2,
                                LocalDate.of(2026, 9, 1),
                                1));

        var actual = generator.generate(input, searchResults);

        ArgumentCaptor<AnswerRequest> request = ArgumentCaptor.forClass(AnswerRequest.class);
        verify(answers).generate(request.capture(), any(LlmStreamHandler.class));
        verify(handler).onToken("강남역 ");
        verify(handler).onRetry(any(Integer.class), any(Throwable.class));
        verify(handler, never()).onComplete();
        verify(handler, never()).onError(any());
        assertThat(request.getValue().executionId()).isEqualTo(31L);
        assertThat(request.getValue().userQuery()).isEqualTo("강남역 근처 매장을 알려줘");
        assertThat(request.getValue().conditions()).containsEntry("location", "강남역");
        assertThat(request.getValue().searchResults()).containsExactlyElementsOf(searchResults);
        assertThat(actual.answer().messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(actual.answer().content()).isEqualTo("강남역 인근 매장을 안내해 드릴게요.");
        assertThat(actual.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(actual.sources()).containsExactly(source);
    }
}
