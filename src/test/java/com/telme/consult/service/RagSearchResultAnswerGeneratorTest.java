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
import com.telme.rag.service.AnswerPromptTemplates;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
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
                                null,
                                "매장",
                                "강남역 인근 매장은 어디인가요?",
                                "강남역 주변 매장을 안내합니다.",
                                0.91,
                                2,
                                LocalDate.of(2026, 9, 1),
                                1, null));

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
        assertThat(actual.answer().followUps()).isEmpty();
    }

    @Test
    void storesSuggestedQuestionsForAnswerBasisAndSearchResultsPassedToRag() {
        when(answers.generate(any(), any()))
                .thenReturn(AnswerResult.builder()
                        .answer("유심 재발급 비용은 7,700원입니다.")
                        .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                        .sources(List.of())
                        .build());
        var searchResults =
                List.of(new FaqSearchResponse(
                        9L, "USIM-0001", "USIM", "유심 재발급 비용이 얼마예요?", "7,700원입니다.",
                        0.91, 1, LocalDate.of(2026, 9, 1), 1, "Q_A"));
        var seen = new ArrayList<Object>();
        var generator = new RagSearchResultAnswerGenerator(
                answers,
                executionId -> handler,
                (answerBasis, results) -> {
                    seen.add(answerBasis);
                    seen.add(results);
                    return List.of("유심 재발급 시 필요한 서류를 알려주세요.");
                });

        var actual = generator.generate(
                new AnswerInput(31L, 7L, 11L, Purpose.GENERAL_FAQ, "유심 얼마예요", "유심 재발급 비용", Map.of()),
                searchResults);

        assertThat(actual.answer().followUps()).containsExactly("유심 재발급 시 필요한 서류를 알려주세요.");
        assertThat(seen).containsExactly(ChatMessage.AnswerBasis.GROUNDED, searchResults);
    }

    @Test
    void singleQuestionWithoutSearchResultSendsGuidanceInsteadOfNoEvidenceSentence() {
        when(answers.generate(any(), any())).thenAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
            stream.onComplete();
            return AnswerResult.builder()
                    .answer(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                    .answerBasis(ChatMessage.AnswerBasis.NO_EVIDENCE)
                    .build();
        });
        var generator = new RagSearchResultAnswerGenerator(answers, executionId -> handler);
        var input = new AnswerInput(31L, 7L, 11L, Purpose.GENERAL_FAQ,
                "파이썬 리스트 정렬 알려줘", "파이썬 리스트 정렬", Map.of());

        var result = generator.generate(input, List.of());

        verify(answers).generate(any(), any());
        verify(handler).onToken(RagSearchResultAnswerGenerator.NO_SEARCH_RESULT_ANSWER);
        verify(handler, never()).onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answer().content())
                .isEqualTo(RagSearchResultAnswerGenerator.NO_SEARCH_RESULT_ANSWER);
        assertThat(result.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
    }

    @Test
    void singleQuestionWithSearchResultKeepsNoEvidenceSentenceWhenModelDeclines() {
        when(answers.generate(any(), any())).thenAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
            return AnswerResult.builder()
                    .answer(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)
                    .answerBasis(ChatMessage.AnswerBasis.NO_EVIDENCE)
                    .build();
        });
        var generator = new RagSearchResultAnswerGenerator(answers, executionId -> handler);
        var input = new AnswerInput(31L, 7L, 11L, Purpose.GENERAL_FAQ,
                "위약금 알려줘", "위약금", Map.of());
        var searchResults = List.of(new FaqSearchResponse(
                9L, null, "요금", "요금제 변경", "다음 날 자정부터 적용됩니다.", 0.75, 1,
                LocalDate.of(2026, 9, 1), 1, null));

        var result = generator.generate(input, searchResults);

        verify(handler).onToken(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answer().content()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void multipleFaqGenerationDoesNotSendIntermediateTokens() {
        when(answers.generate(any(), any())).thenAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            stream.onToken("검증 전 토큰");
            stream.onComplete();
            return AnswerResult.builder()
                    .answer("검증된 답변")
                    .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                    .build();
        });
        var generator = new RagSearchResultAnswerGenerator(answers,
                executionId -> {
                    throw new AssertionError("복합 FAQ 생성 중에는 SSE 핸들러를 만들지 않습니다.");
                });
        var input = new AnswerInput(31L, 7L, 11L, Purpose.GENERAL_FAQ,
                "요금제 종류", "요금제 종류", Map.of(), false);

        var result = generator.generate(input, List.of());

        assertThat(result.answer().content()).isEqualTo("검증된 답변");
        verify(handler, never()).onToken(any());
    }

    // 근거가 여럿인 답변(복합 FAQ, 비교)은 근거마다 하나씩 고르고, 이미 고른 것은 다음 추천으로 바꾼다
    @Test
    void oneFromEachSkipsEmptyAndTakesNextOnDuplicate() {
        assertThat(RagSearchResultAnswerGenerator.SuggestedQuestions.oneFromEach(List.of(
                List.of(), List.of("A", "B"), List.of("A"), List.of("A", "C"))))
                .containsExactly("A", "C");
    }
}
