package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
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

    private ConsultChatProcessingService processor(ConsultChatProcessingService.AnswerProvider answers) {
        return new ConsultChatProcessingService(
                ignored -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        faqTurn(11L, "요금제 종류"), faqTurn(12L, "로밍 신청 방법"))),
                answers, persistence, new ConfirmedConditionConverter(), events);
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
        return new ConsultChatProcessingService.GeneratedAnswer(
                new ChatAnswer(ChatMessage.MessageType.ANSWER, content, basis, List.of(), null), sources);
    }

    private void prepareEvents() {
        when(persistence.startAnswer(EXECUTION_ID, SESSION_ID)).thenReturn(new ChatExecutionState(
                SESSION_ID, EXECUTION_ID, ChatExecution.Status.RUNNING, null, null));
        when(persistence.persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                any(), anyList())).thenReturn(new ChatExecutionState(
                SESSION_ID, EXECUTION_ID, ChatExecution.Status.COMPLETED, null, null));
        when(events.stream(EXECUTION_ID)).thenReturn(stream);
    }
}
