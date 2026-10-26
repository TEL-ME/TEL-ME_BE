package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.MissingCondition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqClarificationDecisionsTest {

    private static final MissingCondition PLAN_TYPE = new MissingCondition(
            "plan_type", "어떤 요금제를 쓰고 계신가요?", List.of("5G", "LTE"), "5G 요금제는 앱에서");
    private static final MissingCondition UNPAID = new MissingCondition(
            "unpaid_bill", "미납 요금이 있으신가요?", List.of("예", "아니요"), "미납이 있으면");

    @Test
    @DisplayName("먼저 물을 조건을 되묻기 판단으로 옮긴다")
    void 되묻기_판단을_만든다() {
        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(PLAN_TYPE)), Map.of());

        assertThat(decision.action()).isEqualTo(Action.ASK);
        assertThat(decision.waitingField()).isEqualTo("plan_type");
        assertThat(decision.message()).isEqualTo("어떤 요금제를 쓰고 계신가요?");
        assertThat(decision.conditions()).containsOnlyKeys("plan_type");
        assertThat(decision.conditions().get("plan_type").status()).isEqualTo(ConditionStatus.PENDING);
    }

    @Test
    @DisplayName("다음에 물을 조건도 함께 남긴다")
    void 남은_조건도_남긴다() {
        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(PLAN_TYPE, UNPAID)), Map.of());

        assertThat(decision.waitingField()).isEqualTo("plan_type");
        assertThat(decision.conditions()).containsOnlyKeys("plan_type", "unpaid_bill");
    }

    @Test
    @DisplayName("이미 받은 조건은 덮어쓰지 않는다")
    void 받은_조건을_지키다() {
        Map<String, Condition> previous =
                Map.of("unpaid_bill", new Condition(ConditionStatus.FILLED, "아니요"));

        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(PLAN_TYPE, UNPAID)), previous);

        assertThat(decision.conditions().get("unpaid_bill").status()).isEqualTo(ConditionStatus.FILLED);
        assertThat(decision.conditions().get("unpaid_bill").value()).isEqualTo("아니요");
    }

    @Test
    @DisplayName("먼저 물을 조건의 선택지를 함께 넘긴다")
    void 선택지를_넘긴다() {
        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(PLAN_TYPE, UNPAID)), Map.of());

        assertThat(decision.options()).containsExactly("5G", "LTE");
    }

    @Test
    @DisplayName("선택지가 없는 조건은 빈 목록으로 넘긴다")
    void 선택지가_없으면_빈_목록이다() {
        MissingCondition typed = new MissingCondition(
                "join_date", "언제 가입하셨나요?", List.of(), "가입일 기준");

        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(typed)), Map.of());

        assertThat(decision.options()).isEmpty();
    }

    @Test
    @DisplayName("화면이 그리는 수를 넘는 선택지는 버린다")
    void 선택지는_세_개까지다() {
        MissingCondition many = new MissingCondition(
                "plan_type", "어떤 요금제를 쓰고 계신가요?", List.of("5G", "LTE", "알뜰", "3G"), "요금제는");

        DialogueDecision decision = FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(many)), Map.of());

        assertThat(decision.options()).containsExactly("5G", "LTE", "알뜰");
    }

    @Test
    @DisplayName("이미 받은 조건을 먼저 물으려 하면 막는다")
    void 받은_조건은_다시_못_묻는다() {
        Map<String, Condition> previous =
                Map.of("plan_type", new Condition(ConditionStatus.FILLED, "5G"));

        assertThatThrownBy(() -> FaqClarificationDecisions.ask(
                1L, new ClarificationPlan(List.of(PLAN_TYPE)), previous))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
