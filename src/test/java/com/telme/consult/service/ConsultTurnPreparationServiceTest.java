package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.consult.converter.ConsultAnalysisConverter;
import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ConsultTurnPreparationServiceTest {
    private final ConsultService consult = mock(ConsultService.class);
    private final ConsultTurnPreparationService service =
            new ConsultTurnPreparationService(
                    new ConsultAnalysisConverter(), new FollowupSelectionValidator(), consult);

    @Test
    void analysisKeepsSavedIdAndConvertsValidValues() {
        var analysis =
                new IntentSubQueryResponse(
                        101L,
                        (short) 1,
                        ConsultRequest.Intent.STORE,
                        "유심 매장",
                        Map.of("location", " 강남역 ", "serviceType", "USIM_REISSUE"));
        service.prepareAnalysis(1L, analysis, LocationStatus.MISSING);
        verify(consult)
                .prepareTurn(
                        1L,
                        101L,
                        Purpose.NEARBY_STORE,
                        Map.of(
                                "location",
                                Condition.filled("강남역"),
                                "serviceType",
                                Condition.filled("USIM_REISSUE")),
                        LocationStatus.MISSING);
    }

    @Test
    void followupKeepsQuestionAndUserMessageReferences() {
        var context = context("STORE");
        var updates = Map.of("location", Condition.filled("강남역"));
        var prepared = result(Action.PROCEED, updates);
        when(consult.prepareTurn(1L, 101L, Purpose.NEARBY_STORE, updates, LocationStatus.MISSING))
                .thenReturn(prepared);
        var value =
                service.prepareFollowup(
                        context,
                        new Selection(101L, 20L, "location", updates),
                        LocationStatus.MISSING);
        assertThat(value.preparation()).isSameAs(prepared);
        assertThat(value.followup().consultRequestId()).isEqualTo(101L);
        assertThat(value.followup().userMessageId()).isEqualTo(30L);
        assertThat(value.followup().answeredField()).isEqualTo("location");
        assertThat(value.originalUserQuery()).isEqualTo("유심 재발급할 매장을 알려줘");
        assertThat(value.searchQuery()).isEqualTo("유심 매장");
    }

    @Test
    void refusalRemainsDeclinedInsteadOfInventedLocation() {
        var updates = Map.of("location", Condition.declined());
        when(consult.prepareTurn(1L, 101L, Purpose.NEARBY_STORE, updates, LocationStatus.MISSING))
                .thenReturn(result(Action.ALTERNATIVE_GUIDANCE, updates));
        var value =
                service.prepareFollowup(
                        context("STORE"),
                        new Selection(101L, 20L, "location", updates),
                        LocationStatus.MISSING);
        assertThat(value.followup().updates().get("location")).isEqualTo(Condition.declined());
        assertThat(value.preparation().prepared().decision().action())
                .isEqualTo(Action.ALTERNATIVE_GUIDANCE);
    }

    @Test
    void correctionWhileWaitingUpdatesConditionWithoutAnsweringQuestion() {
        var context = context("STORE");
        var candidate = context.candidates().getFirst();
        var updates = Map.of("serviceType", Condition.filled("USIM_REISSUE"));
        var pending =
                new ConsultService.PreparationResult(
                        result(Action.ASK, updates).prepared(), candidate.questionMessageId());
        when(consult.prepareTurn(
                        1L,
                        101L,
                        Purpose.NEARBY_STORE,
                        updates,
                        LocationStatus.MISSING))
                .thenReturn(pending);

        var value =
                service.prepareWaitingUpdate(
                        context, new Resolution(candidate, updates), LocationStatus.MISSING);

        assertThat(value.preparation()).isSameAs(pending);
        assertThat(value.originalUserQuery()).isEqualTo("유심 재발급할 매장을 알려줘");
        assertThat(value.searchQuery()).isEqualTo("유심 매장");
        verify(consult)
                .prepareTurn(
                        1L,
                        101L,
                        Purpose.NEARBY_STORE,
                        updates,
                        LocationStatus.MISSING);
    }

    @Test
    void unchangedReplyKeepsPendingQuestionWithoutPreparedChanges() {
        var context = context("STORE");
        var candidate = context.candidates().getFirst();
        var pending = new ConsultService.PreparationResult(null, candidate.questionMessageId());
        when(consult.prepareTurn(
                        1L,
                        101L,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING))
                .thenReturn(pending);

        var value =
                service.prepareWaitingUpdate(
                        context, new Resolution(candidate, Map.of()), LocationStatus.MISSING);

        assertThat(value.preparation()).isSameAs(pending);
        assertThat(value.preparation().prepared()).isNull();
        assertThat(value.originalUserQuery()).isEqualTo("유심 재발급할 매장을 알려줘");
        assertThat(value.searchQuery()).isEqualTo("유심 매장");
    }

    @Test
    void invalidSelectionNeverPreparesAnotherConsultation() {
        assertThatThrownBy(
                        () ->
                                service.prepareFollowup(
                                        context("STORE"),
                                        new Selection(
                                                102L,
                                                20L,
                                                "location",
                                                Map.of("location", Condition.filled("강남역"))),
                                        LocationStatus.MISSING))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(consult);
    }

    private Context context(String intent) {
        return new Context(
                1L,
                30L,
                "강남역이요",
                List.of(
                        new Candidate(
                                101L,
                                "location",
                                20L,
                                "어느 지역인가요?",
                                "유심 재발급할 매장을 알려줘",
                                "유심 매장",
                                intent)));
    }

    private ConsultService.PreparationResult result(Action action, Map<String, Condition> updates) {
        var decision =
                new DialogueDecision(
                        101L,
                        action,
                        updates,
                        action == Action.ASK ? "location" : null,
                        action == Action.PROCEED ? null : "지역을 알려주시면 매장을 찾을 수 있어요.",
                        action == Action.PROCEED ? MessageOrigin.NONE : MessageOrigin.TEMPLATE);
        return new ConsultService.PreparationResult(
                new ConsultService.PreparedTurn(1L, 1, decision), null);
    }
}
