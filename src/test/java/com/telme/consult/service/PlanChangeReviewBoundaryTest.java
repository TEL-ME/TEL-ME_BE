package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.dto.PlanChangeConditions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

class PlanChangeReviewBoundaryTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"요금제 변경은 언제 적용되나요?", "지금 요금제 변경하면 언제부터 적용돼요?", "제가 요금제를 바꾸면 요금은 어떻게 계산돼요?"})
    @DisplayName("적용 시점과 요금 계산 질문은 개인 제한 정책으로 대체하지 않는다")
    void 적용과_계산_질문은_일반_FAQ로_보낸다(String query) {
        assertThat(PlanChangeConditions.isPersonalQuestion(query)).isFalse();
        assertThat(PlanChangeConditions.isCriteriaQuestion(query)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"이번 달엔 안 바꿨어요", "아직 안 바꿨어요", "변경 안 했어요"})
    @DisplayName("현재 변경 이력 질문에 대한 명확한 부정 응답을 저장할 수 있다")
    void 자연스러운_변경_부정을_추출한다(String reply) {
        assertThat(PlanChangeConditions.extract(reply, Set.of(PlanChangeConditions.CHANGED)))
                .isEqualTo(Map.of(PlanChangeConditions.CHANGED, PlanChangeConditions.NO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입한 지 3개월 됐어요", "개통한 지 2개월이 지났어요"})
    @DisplayName("두 달 이상 경과한 명확한 가입 사실은 이번 달 가입이 아님을 확인한다")
    void 충분한_경과_기간은_가입월_아님으로_확인한다(String reply) {
        assertThat(PlanChangeConditions.extract(reply, Set.of(PlanChangeConditions.JOINED)))
                .containsEntry(PlanChangeConditions.JOINED, PlanChangeConditions.NO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가입한 지 한 달 됐어요", "가입한 지 30일 됐어요", "가입한 지 4주 됐어요"})
    @DisplayName("한 달·30일·4주의 경과 기간을 달력상 가입월로 추정하지 않는다")
    void 애매한_경과_기간은_가입월로_바꾸지_않는다(String reply) {
        assertThat(PlanChangeConditions.extract(reply, Set.of(PlanChangeConditions.JOINED)))
                .isEmpty();
    }
}
