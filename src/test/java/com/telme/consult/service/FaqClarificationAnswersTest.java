package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqClarificationAnswersTest {

    private static Candidate waiting(String field, List<String> options) {
        return new Candidate(1L, field, 10L, "질문", "원문", "검색어", "FAQ", options);
    }

    @Test
    @DisplayName("선택지와 똑같은 답이면 대기 중인 조건을 채운다")
    void 선택지를_누르면_채운다() {
        var resolution = new Resolution(
                waiting("existing_bill_status", List.of("예", "아니요")), Map.of());

        Resolution filled = FaqClarificationAnswers.fill(resolution, "아니요");

        assertThat(filled.answersWaitingField()).isTrue();
        assertThat(filled.updates().get("existing_bill_status").status())
                .isEqualTo(ConditionStatus.FILLED);
        assertThat(filled.updates().get("existing_bill_status").value()).isEqualTo("아니요");
    }

    @Test
    @DisplayName("앞뒤 공백은 떼고 비교한다")
    void 공백은_무시한다() {
        var resolution = new Resolution(
                waiting("existing_bill_status", List.of("예", "아니요")), Map.of());

        assertThat(FaqClarificationAnswers.fill(resolution, "  예  ").answersWaitingField()).isTrue();
    }

    @Test
    @DisplayName("선택지에 없는 답은 채우지 않는다")
    void 선택지_밖의_답은_두고_본다() {
        var resolution = new Resolution(
                waiting("existing_bill_status", List.of("예", "아니요")), Map.of());

        assertThat(FaqClarificationAnswers.fill(resolution, "잠깐만요").answersWaitingField()).isFalse();
    }

    @Test
    @DisplayName("선택지가 없는 조건은 채우지 않는다")
    void 선택지가_없으면_두고_본다() {
        var resolution = new Resolution(waiting("join_date", List.of()), Map.of());

        assertThat(FaqClarificationAnswers.fill(resolution, "작년이요").answersWaitingField()).isFalse();
    }

    @Test
    @DisplayName("매장 조건은 라우팅이 뽑으므로 손대지 않는다")
    void 매장_조건은_그대로_둔다() {
        var resolution = new Resolution(waiting("location", List.of("강남역")), Map.of());

        assertThat(FaqClarificationAnswers.fill(resolution, "강남역").answersWaitingField()).isFalse();
    }

    @Test
    @DisplayName("라우팅이 이미 답을 뽑았으면 그대로 둔다")
    void 이미_채워졌으면_그대로_둔다() {
        var resolution = new Resolution(
                waiting("existing_bill_status", List.of("예", "아니요")),
                Map.of("existing_bill_status", Condition.filled("예")));

        Resolution filled = FaqClarificationAnswers.fill(resolution, "아니요");

        assertThat(filled.updates().get("existing_bill_status").value()).isEqualTo("예");
    }

    @Test
    @DisplayName("라우팅이 거절로 읽은 선택지 답은 값으로 되돌린다")
    void 거절로_읽힌_선택지_답을_되돌린다() {
        var resolution = new Resolution(
                waiting("self_presentation", List.of("예", "아니요")),
                Map.of("self_presentation", Condition.declined()));

        Resolution filled = FaqClarificationAnswers.fill(resolution, "아니요");

        assertThat(filled.updates().get("self_presentation").status())
                .isEqualTo(ConditionStatus.FILLED);
        assertThat(filled.updates().get("self_presentation").value()).isEqualTo("아니요");
    }

    @Test
    @DisplayName("선택지에 없는 말로 거절하면 거절 그대로 둔다")
    void 선택지_밖의_거절은_그대로_둔다() {
        var resolution = new Resolution(
                waiting("self_presentation", List.of("예", "아니요")),
                Map.of("self_presentation", Condition.declined()));

        Resolution filled = FaqClarificationAnswers.fill(resolution, "말하기 싫어요");

        assertThat(filled.updates().get("self_presentation").status())
                .isEqualTo(ConditionStatus.DECLINED);
    }
}
