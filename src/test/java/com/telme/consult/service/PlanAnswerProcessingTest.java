package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.NoAnswerSuggestions;
import com.telme.llm.service.LlmStreamHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 실제 상담 처리 흐름(저장·전달)에서 요금제 답변이 FAQ 검색 없이 그대로 나가는지 확인한다
class PlanAnswerProcessingTest {
    private static final long EXECUTION_ID = 41L;
    private static final long SESSION_ID = 8L;
    private static final String PLAN_QUESTION = "요금제 종류 알려줘";
    private static final String PLAN_ANSWER = "요금제 안내예요.\n\n[5G]\n- 5G 스탠다드: 월 65,000원";

    private final ConsultChatPersistenceService persistence = mock(ConsultChatPersistenceService.class);
    private final ConsultChatEvents events = mock(ConsultChatEvents.class);
    private final LlmStreamHandler stream = mock(LlmStreamHandler.class);
    private final AnswerProvider faq = mock(AnswerProvider.class);
    private final PlanQuestionClassifier classifier = mock(PlanQuestionClassifier.class);
    private final PlanCatalogAnswerer catalog = mock(PlanCatalogAnswerer.class);
    private final MemberRecommendationAnswerer recommendation = mock(MemberRecommendationAnswerer.class);
    private final ChatProcessingCommand command = new ChatProcessingCommand(EXECUTION_ID, SESSION_ID, 9L, PLAN_QUESTION);

    @Test
    void 요금제_목록_질문은_FAQ_검색_없이_저장되고_한_번만_전달된다() {
        stubPlanAnswer();
        prepareEvents();
        var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision(11L));
        var processor = processor(ignored -> new AnalyzedTurn(
                new ConsultService.PreparationResult(prepared, null), null, Purpose.GENERAL_FAQ, PLAN_QUESTION,
                PLAN_QUESTION));

        processor.request(command);

        ArgumentCaptor<ChatAnswer> saved = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence, atLeastOnce()).persistFinalAnswer(
                eq(EXECUTION_ID), eq(SESSION_ID), eq(11L), anyInt(), saved.capture(), anyList());
        assertThat(saved.getValue().content()).isEqualTo(PLAN_ANSWER);
        assertThat(saved.getValue().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.GROUNDED);
        verify(stream).onToken(PLAN_ANSWER);
        verify(faq, never()).generate(any());
        verify(faq, never()).generate(any(), any());
    }

    @Test
    void 복합_질문에서도_요금제_부분은_근거_없음_문구로_바뀌지_않고_나머지는_FAQ가_답한다() {
        stubPlanAnswer();
        when(faq.generate(any())).thenReturn(GeneratedAnswer.withoutSources(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "신고 절차 FAQ 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), null)));
        when(faq.generate(any(), any())).thenReturn(GeneratedAnswer.withoutSources(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "신고 절차 FAQ 답변", ChatMessage.AnswerBasis.GROUNDED, List.of(), null)));
        when(faq.prepare(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(ConsultChatProcessingService.Prepared.none());
        prepareEvents();
        var processor = processor(ignored -> AnalyzedTurn.compound(List.of(
                faqTurn(11L, PLAN_QUESTION), faqTurn(12L, "분실 신고 절차"))));

        processor.request(command);

        ArgumentCaptor<ChatAnswer> saved = ArgumentCaptor.forClass(ChatAnswer.class);
        verify(persistence).persistFinalAnswers(eq(EXECUTION_ID), eq(SESSION_ID), anyList(), saved.capture(), anyList());
        assertThat(saved.getValue().content()).contains(PLAN_ANSWER, "신고 절차 FAQ 답변");
    }

    @Test
    void 나이대를_모르면_답변_메시지를_열기_전에_나이대를_선택지로_되묻는다() {
        when(faq.clarifies()).thenReturn(true);
        when(classifier.classifyQuestion(anyLong(), eq("요금제 추천해줘"))).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        MissingCondition ask = new MissingCondition("age_group", "나이대가 어떻게 되세요?",
                List.of("만 14~18세", "만 19~64세", "만 65세 이상"), null);
        when(recommendation.recommend(any(), any(), any(), eq(true)))
                .thenReturn(new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(ask)));
        prepareEvents();
        var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision(11L));
        var processor = processor(ignored -> new AnalyzedTurn(
                new ConsultService.PreparationResult(prepared, null), null, Purpose.GENERAL_FAQ, "요금제 추천해줘",
                "요금제 추천해줘"));

        processor.request(new ChatProcessingCommand(EXECUTION_ID, SESSION_ID, 9L, "요금제 추천해줘"));

        ArgumentCaptor<ConsultService.PreparedTurn> asking = ArgumentCaptor.forClass(ConsultService.PreparedTurn.class);
        verify(persistence).persistClarification(eq(EXECUTION_ID), asking.capture(), any());
        assertThat(asking.getValue().decision().action()).isEqualTo(DialogueDecision.Action.ASK);
        assertThat(asking.getValue().decision().message()).isEqualTo("나이대가 어떻게 되세요?");
        assertThat(asking.getValue().decision().options()).containsExactly("만 14~18세", "만 19~64세", "만 65세 이상");
        verify(persistence, never()).startAnswer(anyLong(), anyLong());
        verify(faq, never()).generate(any());
    }

    private void stubPlanAnswer() {
        when(classifier.classifyQuestion(anyLong(), eq(PLAN_QUESTION))).thenReturn(PlanQuestion.of(PlanQuestionType.PLAN_CATALOG));
        when(classifier.classifyQuestion(anyLong(), eq("분실 신고 절차"))).thenReturn(PlanQuestion.of(PlanQuestionType.NONE));
        when(catalog.answer(PLAN_QUESTION)).thenReturn(PLAN_ANSWER);
    }

    private ConsultChatProcessingService processor(ConsultChatProcessingService.TurnAnalyzer analyzer) {
        AnswerProvider answers = new PlanAnswerProvider(faq, classifier, catalog, mock(MemberPlanAnswerer.class), recommendation,
                sessionId -> java.util.Optional.empty(), sessionId -> Map.of(), ComparisonEvidenceResolver.passthrough(),
                mock(ExecutionTrace.class), true);
        return new ConsultChatProcessingService(analyzer, answers, persistence, new ConfirmedConditionConverter(),
                events, mock(ExecutionTrace.class), com.telme.consult.repository.AskedQuestions.none(),
                NoAnswerSuggestions.none(), RagSearchResultAnswerGenerator.SuggestedQuestions.none());
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
        when(events.stream(EXECUTION_ID)).thenReturn(stream);
    }
}
