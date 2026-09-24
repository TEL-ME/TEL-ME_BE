package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.entity.ChatMessage;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.FollowupAnalysis;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.ResolvedFollowup;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ConsultTurnAnalysisAdapterTest {
    private final ConsultTurnPreparationService preparation =
            mock(ConsultTurnPreparationService.class);

    @Test
    void mismatchedMessageCannotReachAnalysis() {
        var analysis = mock(ConsultTurnAnalysisAdapter.AnalysisProvider.class);
        var adapter =
                new ConsultTurnAnalysisAdapter(
                        command -> new Context(1, 99, "강남역이요", List.of()),
                        analysis,
                        preparation,
                        new FollowupConditionConverter());
        assertThatThrownBy(() -> adapter.analyze(new ChatProcessingCommand(3L, 1L, 10L, "강남역이요")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(analysis, preparation);
    }

    @Test
    void followupKeepsExistingConsultationAndAnsweredField() {
        var context =
                new Context(
                        1,
                        10,
                        "강남역이요",
                        List.of(
                                new Candidate(
                                        101L,
                                        "location",
                                        9L,
                                        "어느 지역인가요?",
                                        "유심 재발급할 매장을 알려줘",
                                        "유심 매장",
                                        "STORE")));
        var selection =
                new Selection(101, 9, "location", Map.of("location", Condition.filled("강남역")));
        var result = new ConsultService.PreparationResult(null, 9L);
        when(preparation.prepareFollowup(any(), any(), any()))
                .thenReturn(
                        new ConsultTurnPreparationService.PreparedFollowup(
                                result,
                                new ResolvedFollowup(101, 10, "location", selection.updates()),
                                Purpose.NEARBY_STORE,
                                "유심 재발급할 매장을 알려줘",
                                "유심 매장"));
        var adapter =
                new ConsultTurnAnalysisAdapter(
                        command -> context,
                        value ->
                                new AnalysisResult(
                                        null,
                                        new FollowupAnalysis(
                                                101L, Map.of("location", "강남역")),
                                        LocationStatus.MISSING),
                        preparation,
                        new FollowupConditionConverter());
        var turn = adapter.analyze(new ChatProcessingCommand(3L, 1L, 10L, "강남역이요"));
        assertThat(turn.preparation()).isSameAs(result);
        assertThat(turn.answeredField()).isEqualTo("location");
        assertThat(turn.originalUserQuery()).isEqualTo("유심 재발급할 매장을 알려줘");
        assertThat(turn.searchQuery()).isEqualTo("유심 매장");
    }

    @Test
    void initialQuestionUsesStoredAnalysisIdWithoutFollowupField() {
        var context = new Context(1, 10, "요금 납부 방법", List.of());
        var query =
                new IntentSubQueryResponse(
                        101L, (short) 1, ConsultRequest.Intent.FAQ, "요금 납부 방법", Map.of());
        var result = new ConsultService.PreparationResult(null, 9L);
        when(preparation.prepareAnalysis(1, query, LocationStatus.MISSING)).thenReturn(result);
        var adapter =
                new ConsultTurnAnalysisAdapter(
                        command -> context,
                        value -> new AnalysisResult(query, null, LocationStatus.MISSING),
                        preparation,
                        new FollowupConditionConverter());
        var turn = adapter.analyze(new ChatProcessingCommand(3L, 1L, 10L, "요금 납부 방법"));
        assertThat(turn.preparation()).isSameAs(result);
        assertThat(turn.answeredField()).isNull();
        assertThat(turn.originalUserQuery()).isEqualTo("요금 납부 방법");
        assertThat(turn.searchQuery()).isEqualTo("요금 납부 방법");
    }

    @Test
    void directGuidanceDoesNotPrepareConsultation() {
        var context = new Context(1, 10, "오늘 날씨 어때?", List.of());
        var answer =
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        "통신 관련 질문을 입력해 주세요.",
                        ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                        List.of(),
                        null);
        var adapter =
                new ConsultTurnAnalysisAdapter(
                        command -> context,
                        value -> AnalysisResult.direct(answer),
                        preparation,
                        new FollowupConditionConverter());

        var turn = adapter.analyze(new ChatProcessingCommand(3L, 1L, 10L, "오늘 날씨 어때?"));

        assertThat(turn.directAnswer()).isSameAs(answer);
        assertThat(turn.preparation()).isNull();
        verifyNoInteractions(preparation);
    }

    @Test
    void correctionWhileWaitingKeepsQuestionWithoutMarkingItAnswered() {
        var context =
                new Context(
                        1,
                        10,
                        "유심 재발급으로 바꿀게요",
                        List.of(
                                new Candidate(
                                        101L,
                                        "location",
                                        9L,
                                        "어느 지역인가요?",
                                        "유심 재발급할 매장을 알려줘",
                                        "유심 매장",
                                        "STORE")));
        when(preparation.prepareWaitingUpdate(any(), any(), any()))
                .thenReturn(
                        new ConsultTurnPreparationService.PreparedWaitingUpdate(
                                new ConsultService.PreparationResult(
                                        new ConsultService.PreparedTurn(
                                                1L,
                                                1,
                                                new DialogueDecision(
                                                        101L,
                                                        Action.ASK,
                                                        Map.of(
                                                                "serviceType",
                                                                Condition.filled("USIM_REISSUE")),
                                                        "location",
                                                        "어느 지역인가요?",
                                                        MessageOrigin.TEMPLATE)),
                                                9L),
                                Purpose.NEARBY_STORE,
                                "유심 재발급할 매장을 알려줘",
                                "유심 매장"));
        var adapter =
                new ConsultTurnAnalysisAdapter(
                        command -> context,
                        value ->
                                new AnalysisResult(
                                        null,
                                        new FollowupAnalysis(
                                                101L,
                                                Map.of("serviceType", "USIM_REISSUE")),
                                        LocationStatus.MISSING),
                        preparation,
                        new FollowupConditionConverter());

        var turn =
                adapter.analyze(
                        new ChatProcessingCommand(3L, 1L, 10L, "유심 재발급으로 바꿀게요"));

        assertThat(turn.preparation().waitingForReply()).isTrue();
        assertThat(turn.answeredField()).isNull();
        assertThat(turn.originalUserQuery()).isEqualTo("유심 재발급할 매장을 알려줘");
        assertThat(turn.searchQuery()).isEqualTo("유심 매장");
    }

    @Test
    void missingAnalysisKindIsRejected() {
        assertThatThrownBy(() -> new AnalysisResult(null, null, LocationStatus.MISSING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
