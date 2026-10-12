package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatOutputMessage;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.service.AnswerPromptTemplates;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultMultiFaqProcessingTest {
    private static final long EXECUTION_ID = 31L;
    private static final long SESSION_ID = 7L;
    private final ConsultChatPersistenceService persistence = mock(ConsultChatPersistenceService.class);
    private final ConsultChatEvents events = mock(ConsultChatEvents.class);
    private final LlmStreamHandler stream = mock(LlmStreamHandler.class);
    private final ExecutionTrace trace = mock(ExecutionTrace.class);
    private final ChatProcessingCommand command = new ChatProcessingCommand(
            EXECUTION_ID, SESSION_ID, 9L, "요금제와 로밍 신청 방법 알려줘");

    @Test
    void oneMissingEvidenceKeepsOtherAnswerAndItsSource() {
        prepareEvents();
        AnswerSource source = new AnswerSource(null, "요금제 근거", 1, null, (short) 1, null);
        var processor = processor(input -> {
            assertThat(input.streamTokens()).isFalse();
            if (input.originalUserQuery().equals("요금제 종류")) {
                return generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(source));
            }
            return generated(AnswerPromptTemplates.NO_EVIDENCE_ANSWER,
                    ChatMessage.AnswerBasis.NO_EVIDENCE, List.of());
        });

        processor.request(command);

        ArgumentCaptor<ChatAnswer> answer = ArgumentCaptor.forClass(ChatAnswer.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnswerSource>> sources = ArgumentCaptor.forClass(List.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                answer.capture(), sources.capture());
        assertThat(answer.getValue().content()).contains(
                "1. 요금제 종류\n요금제 답변",
                "2. 로밍 신청 방법\n" + AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(answer.getValue().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        assertThat(sources.getValue()).containsExactly(source);
        verify(stream).onToken(answer.getValue().content());
        verify(trace).stage(EXECUTION_ID, "finalTransmission", Map.of(
                "outputMessageId", 99L, "status", "DISPATCH_RETURNED"));
    }

    @Test
    void recordsFailedTransmissionWithoutRevertingCompletedAnswer() {
        prepareEvents();
        doThrow(new IllegalStateException("연결 종료")).when(stream).onToken(any());
        var processor = processor(input -> generated("답변", ChatMessage.AnswerBasis.GROUNDED, List.of()));

        processor.request(command);

        verify(trace).stage(EXECUTION_ID, "finalTransmission", Map.of(
                "outputMessageId", 99L, "status", "DISPATCH_ERROR"));
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(), any(), anyList());
        verify(events).completed(eq(EXECUTION_ID), any());
        verify(persistence, never()).failAnswer(anyLong(), anyLong(), any());
        verify(events, never()).failed(anyLong(), any());
    }

    @Test
    void noEvidenceForEveryQuestionCompletesWithoutSources() {
        prepareEvents();
        var processor = processor(input -> generated(AnswerPromptTemplates.NO_EVIDENCE_ANSWER,
                ChatMessage.AnswerBasis.NO_EVIDENCE, List.of()));

        processor.request(command);

        ArgumentCaptor<ChatAnswer> answer = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                answer.capture(), eq(List.of()));
        assertThat(answer.getValue().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
        assertThat(answer.getValue().content()).contains("1. 요금제 종류", "2. 로밍 신청 방법");
    }

    @Test
    void eachQuestionUsesOnlyItsOwnTextForSearchAndAnswer() {
        prepareEvents();
        List<ConsultChatProcessingService.AnswerInput> inputs = new java.util.ArrayList<>();
        var processor = processor(input -> {
            inputs.add(input);
            return generated("답변", ChatMessage.AnswerBasis.GROUNDED, List.of());
        });

        processor.request(command);

        assertThat(inputs).extracting(ConsultChatProcessingService.AnswerInput::originalUserQuery)
                .containsExactly("요금제 종류", "로밍 신청 방법");
        assertThat(inputs).extracting(ConsultChatProcessingService.AnswerInput::searchQuery)
                .containsExactly("요금제 종류", "로밍 신청 방법");
    }

    @Test
    void displayTextDoesNotChangeNormalizedSearchQuery() {
        prepareEvents();
        var displayCommand = new ChatProcessingCommand(
                EXECUTION_ID, SESSION_ID, 9L, "번호이동 서류는 뭐고 요금제 변경은 어떻게 해?");
        List<ConsultChatProcessingService.AnswerInput> inputs = new java.util.ArrayList<>();
        var processor = new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        faqTurn(11L, "번호이동서류는뭐고"),
                        faqTurn(12L, "요금제변경은어떻게해"))),
                input -> {
                    inputs.add(input);
                    return generated("답변", ChatMessage.AnswerBasis.GROUNDED, List.of());
                },
                persistence, new ConfirmedConditionConverter(), events, trace);

        processor.request(displayCommand);

        ArgumentCaptor<ChatAnswer> answer = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                answer.capture(), anyList());
        assertThat(answer.getValue().content()).contains("번호이동 서류는 뭐고", "요금제 변경은 어떻게 해")
                .doesNotContain("번호이동서류는뭐고", "요금제변경은어떻게해");
        assertThat(inputs).extracting(ConsultChatProcessingService.AnswerInput::searchQuery)
                .containsExactly("번호이동서류는뭐고", "요금제변경은어떻게해");
        assertThat(inputs).extracting(ConsultChatProcessingService.AnswerInput::candidateEvidenceQuery)
                .containsExactly("번호이동 서류는 뭐고", "요금제 변경은 어떻게 해");
    }

    @Test
    void eachQuestionKeepsPreviousConversationContext() {
        prepareEvents();
        ChatContext context = new ChatContext(SESSION_ID, 9L, null, List.of(),
                "요금제와 로밍 신청 방법 알려줘", 0);
        List<ConsultChatProcessingService.AnswerInput> inputs = new java.util.ArrayList<>();
        var processor = new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        faqTurn(11L, "요금제 종류"), faqTurn(12L, "로밍 신청 방법")))
                        .withContext(context, "요금제와 로밍 신청 방법 알려줘"),
                input -> {
                    inputs.add(input);
                    return generated("답변", ChatMessage.AnswerBasis.GROUNDED, List.of());
                }, persistence, new ConfirmedConditionConverter(), events, trace);

        processor.request(command);

        assertThat(inputs).hasSize(2);
        assertThat(inputs).allSatisfy(input -> {
            assertThat(input.context()).isSameAs(context);
            assertThat(input.streamTokens()).isFalse();
            assertThat(input.resolvedUserQuery()).isEqualTo(input.originalUserQuery());
        });
    }

    @Test
    void sameFaqCitedByBothQuestionsIsSavedOnce() {
        prepareEvents();
        AnswerSource shared = new AnswerSource(5L, "공통 근거", 1, null, (short) 1, null);
        AnswerSource roaming = new AnswerSource(6L, "로밍 근거", 1, null, (short) 2, null);
        var processor = processor(input -> input.originalUserQuery().equals("요금제 종류")
                ? generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(shared))
                : generated("로밍 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(shared, roaming)));

        processor.request(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnswerSource>> sources = ArgumentCaptor.forClass(List.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                any(), sources.capture());
        assertThat(sources.getValue()).containsExactly(shared, roaming);
    }

    @Test
    void generationFailureDoesNotSavePartialAnswerOrSendTokens() {
        prepareEvents();
        AtomicInteger calls = new AtomicInteger();
        var processor = processor(input -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("모델 실패");
            }
            return generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of());
        });

        processor.request(command);

        assertThat(calls.get()).isEqualTo(2);
        verify(persistence, never()).persistFinalAnswers(anyLong(), anyLong(),
                anyList(), any(), anyList());
        verify(persistence).failAnswer(eq(EXECUTION_ID), eq(SESSION_ID), any());
        verify(stream, never()).onToken(any());
        verify(events).failed(eq(EXECUTION_ID), any());
    }

    // 합친 답변에는 근거가 있는 하위 답변마다 추천 질문을 하나씩 붙인다
    @Test
    void combinedAnswerTakesFirstFollowUpOfEachGroundedAnswer() {
        prepareEvents();
        var processor = processor(input -> input.originalUserQuery().equals("요금제 종류")
                ? generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(),
                        List.of("요금제 변경 방법", "요금제 할인"))
                : generated("로밍 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(),
                        List.of("로밍 요금", "로밍 해지")));

        processor.request(command);

        assertThat(savedAnswer().followUps()).containsExactly("요금제 변경 방법", "로밍 요금");
    }

    @Test
    void duplicateFollowUpIsReplacedByNextOfSameAnswer() {
        prepareEvents();
        var processor = processor(input -> input.originalUserQuery().equals("요금제 종류")
                ? generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(),
                        List.of("가까운 매장을 알려주세요.", "요금제 할인"))
                : generated("로밍 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(),
                        List.of("가까운 매장을 알려주세요.", "로밍 요금")));

        processor.request(command);

        assertThat(savedAnswer().followUps()).containsExactly("가까운 매장을 알려주세요.", "로밍 요금");
    }

    // 근거 없는 하위 답변은 건너뛰고, 추천이 없는 하위 답변은 자리를 차지하지 않는다
    @Test
    void followUpsComeOnlyFromGroundedAnswers() {
        prepareEvents();
        var processor = processor(input -> input.originalUserQuery().equals("요금제 종류")
                ? generated("요금제 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), List.of())
                : generated(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, ChatMessage.AnswerBasis.NO_EVIDENCE,
                        List.of(), List.of("로밍 요금")));

        processor.request(command);

        assertThat(savedAnswer().followUps()).isEmpty();
    }

    // 서로 이어진 질문을 함께 물으면 하위 답변끼리 상대 질문을 추천하므로, 답한 정책의 추천은 빼고 다음 추천을 쓴다
    @Test
    void followUpAboutAnotherAnsweredQuestionIsSkipped() {
        prepareEvents();
        AnswerSource usimCost = new AnswerSource(5L, "유심 비용", 1, null, (short) 1, null);
        AnswerSource usimDocs = new AnswerSource(6L, "유심 서류", 1, null, (short) 1, null);
        var suggested = new RagSearchResultAnswerGenerator.SuggestedQuestions() {
            @Override
            public List<String> suggest(ChatMessage.AnswerBasis basis, List<com.telme.faq.dto.res.FaqSearchResponse> r) {
                return List.of();
            }

            @Override
            public java.util.Set<String> questionsAbout(java.util.Collection<Long> faqIds) {
                assertThat(faqIds).containsExactly(5L, 6L);
                return java.util.Set.of("유심 재발급 시 필요한 서류를 알려주세요.", "유심 새로 받는 데 얼마 들어요");
            }
        };
        var processor = new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        faqTurn(11L, "요금제 종류"), faqTurn(12L, "로밍 신청 방법"))),
                input -> input.originalUserQuery().equals("요금제 종류")
                        ? generated("비용 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(usimCost),
                                List.of("유심 재발급 시 필요한 서류를 알려주세요.", "유심 재발급 가능한 매장을 알려주세요."))
                        : generated("서류 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(usimDocs),
                                List.of("유심 새로 받는 데 얼마 들어요", "평일이랑 토요일 운영시간이 어떻게 다른가요?")),
                persistence, new ConfirmedConditionConverter(), events, trace,
                com.telme.consult.repository.AskedQuestions.none(),
                ConsultChatProcessingService.NoAnswerSuggestions.none(), suggested);

        processor.request(command);

        assertThat(savedAnswer().followUps())
                .containsExactly("유심 재발급 가능한 매장을 알려주세요.", "평일이랑 토요일 운영시간이 어떻게 다른가요?");
    }

    private ChatAnswer savedAnswer() {
        ArgumentCaptor<ChatAnswer> answer = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                answer.capture(), anyList());
        return answer.getValue();
    }

    private ConsultChatProcessingService processor(ConsultChatProcessingService.AnswerProvider answers) {
        return new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        faqTurn(11L, "요금제 종류"), faqTurn(12L, "로밍 신청 방법"))),
                answers, persistence, new ConfirmedConditionConverter(), events, trace);
    }

    private ConsultChatProcessingService.FaqTurn faqTurn(long requestId, String queryText) {
        var decision = new DialogueDecision(requestId, DialogueDecision.Action.PROCEED,
                Map.of(), null, null, DialogueDecision.MessageOrigin.NONE);
        var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision);
        return new ConsultChatProcessingService.FaqTurn(
                new ConsultService.PreparationResult(prepared, null), queryText);
    }

    private ConsultChatProcessingService.GeneratedAnswer generated(
            String content, ChatMessage.AnswerBasis basis, List<AnswerSource> sources) {
        return generated(content, basis, sources, List.of());
    }

    private ConsultChatProcessingService.GeneratedAnswer generated(
            String content, ChatMessage.AnswerBasis basis, List<AnswerSource> sources, List<String> followUps) {
        return new ConsultChatProcessingService.GeneratedAnswer(
                new ChatAnswer(ChatMessage.MessageType.ANSWER, content, basis, followUps, null), sources);
    }

    private void prepareEvents() {
        when(persistence.startAnswer(EXECUTION_ID, SESSION_ID)).thenReturn(new ChatExecutionState(
                SESSION_ID, EXECUTION_ID, ChatExecution.Status.RUNNING, null, null));
        when(persistence.persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                any(), anyList())).thenReturn(new ChatExecutionState(
                SESSION_ID, EXECUTION_ID, ChatExecution.Status.COMPLETED, null,
                new ChatOutputMessage(SESSION_ID, EXECUTION_ID, 99L, 3,
                        ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED)));
        when(events.stream(EXECUTION_ID)).thenReturn(stream);
    }
}
