package com.telme.consult.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.global.common.exception.GeneralException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class FollowupConditionConverterTest {
    private final FollowupConditionConverter converter = new FollowupConditionConverter();

    @Test
    void convertsRoutingMapWithoutExposingConsultConditionModel() {
        var selection =
                converter.convert(
                        context(),
                        101L,
                        Map.of("location", " 강남역 ", "serviceType", "USIM_REISSUE"));

        assertThat(selection.consultRequestId()).isEqualTo(101L);
        assertThat(selection.questionMessageId()).isEqualTo(20L);
        assertThat(selection.field()).isEqualTo("location");
        assertThat(selection.updates())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "location",
                                Condition.filled("강남역"),
                                "serviceType",
                                Condition.filled("USIM_REISSUE")));
    }

    @Test
    void staleConsultationCannotUpdateAnotherPendingQuestion() {
        assertThatThrownBy(
                        () ->
                                converter.convert(
                                        context(), 999L, Map.of("location", "강남역")))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void missingAnsweredConditionIsRejected() {
        var resolution =
                converter.resolve(
                        context(), 101L, Map.of("serviceType", "USIM_REISSUE"));

        assertThat(resolution.answersWaitingField()).isFalse();
        assertThat(resolution.updates())
                .containsEntry("serviceType", Condition.filled("USIM_REISSUE"));
        assertThatThrownBy(resolution::toSelection).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void convertsExplicitDeclineWithoutTreatingItAsMissing() {
        var resolution = converter.resolve(context(), 101L, Map.of(), Set.of("location"));

        assertThat(resolution.answersWaitingField()).isTrue();
        assertThat(resolution.updates()).containsEntry("location", Condition.declined());
    }

    @Test
    void keepsWaitingWhenFollowupContainsNoCondition() {
        var resolution = converter.resolve(context(), 101L, Map.of(), Set.of());

        assertThat(resolution.answersWaitingField()).isFalse();
        assertThat(resolution.updates()).isEmpty();
    }

    private Context context() {
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
                                "STORE")));
    }
}
