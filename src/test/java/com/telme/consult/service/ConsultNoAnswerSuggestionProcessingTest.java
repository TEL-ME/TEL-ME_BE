package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatOutputMessage;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.NoAnswerSuggestions;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.AnswerPromptTemplates;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

// 근거가 없어 답을 못 했을 때만 "혹시 이런 내용을 찾으셨나요?" 버튼을 붙인다
class ConsultNoAnswerSuggestionProcessingTest {
    private static final long EXECUTION_ID = 31L;
    private static final long SESSION_ID = 7L;
    private static final String QUESTION = "지하철에서 폰을 놓고 내렸습니다. 지금 당장 뭘 해야 할까요";
    private static final String SUGGESTED = "폰 잃어버리면 정지부터 해야 해요?";

    private final ConsultChatPersistenceService persistence = mock(ConsultChatPersistenceService.class);
    private final ConsultChatEvents events = mock(ConsultChatEvents.class);
    private final NoAnswerSuggestions noAnswer = mock(NoAnswerSuggestions.class);
    private final ChatProcessingCommand command = new ChatProcessingCommand(EXECUTION_ID, SESSION_ID, 9L, QUESTION);

    @Test
    void noEvidenceAnswerGetsFaqQuestionFoundWithUserQuestion() {
        when(noAnswer.suggest(QUESTION)).thenReturn(List.of(SUGGESTED));

        ChatAnswer saved = processSingle(Purpose.GENERAL_FAQ, answer(ChatMessage.AnswerBasis.NO_EVIDENCE));

        assertThat(saved.followUps()).containsExactly(SUGGESTED);
        assertThat(saved.content()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER + "\n\n"
                + ConsultChatProcessingService.NO_ANSWER_SUGGESTION_GUIDE);
        assertThat(saved.answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
    }

    // 맞는 후보가 없으면 안내 문장도 붙이지 않는다
    @Test
    void noCandidateKeepsAnswerAsIs() {
        when(noAnswer.suggest(QUESTION)).thenReturn(List.of());

        ChatAnswer saved = processSingle(Purpose.GENERAL_FAQ, answer(ChatMessage.AnswerBasis.NO_EVIDENCE));

        assertThat(saved.followUps()).isEmpty();
        assertThat(saved.content()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void groundedAnswerAndStoreSearchDoNotLookForCandidate() {
        processSingle(Purpose.GENERAL_FAQ, answer(ChatMessage.AnswerBasis.GROUNDED));
        processSingle(Purpose.NEARBY_STORE, answer(ChatMessage.AnswerBasis.NO_EVIDENCE));

        verifyNoInteractions(noAnswer);
    }

    // 질문을 쪼갠 답변은 하위 질문이 모두 근거 없을 때만, 쪼개기 전 원문으로 찾는다
    @Test
    void multipleFaqWithoutAnyEvidenceUsesOriginalQuestion() {
        when(noAnswer.suggest(QUESTION)).thenReturn(List.of(SUGGESTED));

        ChatAnswer saved = processMultiple(answer(ChatMessage.AnswerBasis.NO_EVIDENCE),
                answer(ChatMessage.AnswerBasis.NO_EVIDENCE));

        assertThat(saved.followUps()).containsExactly(SUGGESTED);
        assertThat(saved.content()).contains("1. 신고 절차", "2. 회수 방법")
                .endsWith(ConsultChatProcessingService.NO_ANSWER_SUGGESTION_GUIDE);
    }

    @Test
    void multipleFaqWithSomeEvidenceDoesNotLookForCandidate() {
        processMultiple(answer(ChatMessage.AnswerBasis.GROUNDED), answer(ChatMessage.AnswerBasis.NO_EVIDENCE));

        verifyNoInteractions(noAnswer);
    }

    private ChatAnswer processSingle(Purpose purpose, ChatAnswer generated) {
        prepareEvents();
        var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision(11L));
        var processor = processor(ignored -> new AnalyzedTurn(
                new ConsultService.PreparationResult(prepared, null), null, purpose, QUESTION, QUESTION),
                input -> GeneratedAnswer.withoutSources(generated));

        processor.request(command);

        ArgumentCaptor<ChatAnswer> saved = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence, org.mockito.Mockito.atLeastOnce()).persistFinalAnswer(
                eq(EXECUTION_ID), eq(SESSION_ID), eq(11L), anyInt(), saved.capture(), anyList());
        return saved.getValue();
    }

    private ChatAnswer processMultiple(ChatAnswer first, ChatAnswer second) {
        prepareEvents();
        var processor = processor(ignored -> AnalyzedTurn.compound(List.of(
                        faqTurn(11L, "신고 절차"), faqTurn(12L, "회수 방법"))),
                input -> GeneratedAnswer.withoutSources(input.originalUserQuery().equals("신고 절차") ? first : second));

        processor.request(command);

        ArgumentCaptor<ChatAnswer> saved = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(),
                saved.capture(), anyList());
        return saved.getValue();
    }

    private ConsultChatProcessingService processor(
            ConsultChatProcessingService.TurnAnalyzer analyzer, ConsultChatProcessingService.AnswerProvider answers) {
        return new ConsultChatProcessingService(analyzer, answers, persistence, new ConfirmedConditionConverter(),
                events, mock(ExecutionTrace.class), com.telme.consult.repository.AskedQuestions.none(), noAnswer,
                RagSearchResultAnswerGenerator.SuggestedQuestions.none());
    }

    private static ChatAnswer answer(ChatMessage.AnswerBasis basis) {
        String content = basis == ChatMessage.AnswerBasis.GROUNDED ? "답변" : AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
        return new ChatAnswer(ChatMessage.MessageType.ANSWER, content, basis, List.of(), null);
    }

    private static DialogueDecision decision(long requestId) {
        return new DialogueDecision(requestId, DialogueDecision.Action.PROCEED,
                Map.of(), null, null, DialogueDecision.MessageOrigin.NONE);
    }

    private static ConsultChatProcessingService.ConsultTurn faqTurn(long requestId, String queryText) {
        var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision(requestId));
        return new ConsultChatProcessingService.ConsultTurn(
                new ConsultService.PreparationResult(prepared, null), queryText);
    }

    private void prepareEvents() {
        var completed = new ChatExecutionState(SESSION_ID, EXECUTION_ID, ChatExecution.Status.COMPLETED, null,
                new ChatOutputMessage(SESSION_ID, EXECUTION_ID, 99L, 3,
                        ChatMessage.MessageType.ANSWER, ChatMessage.Status.COMPLETED));
        when(persistence.startAnswer(EXECUTION_ID, SESSION_ID)).thenReturn(new ChatExecutionState(
                SESSION_ID, EXECUTION_ID, ChatExecution.Status.RUNNING, null, null));
        when(persistence.persistFinalAnswer(anyLong(), anyLong(), anyLong(), anyInt(), any(), anyList()))
                .thenReturn(completed);
        when(persistence.persistFinalAnswers(anyLong(), anyLong(), anyList(), any(), anyList()))
                .thenReturn(completed);
        when(events.stream(EXECUTION_ID)).thenReturn(mock(LlmStreamHandler.class));
    }
}
