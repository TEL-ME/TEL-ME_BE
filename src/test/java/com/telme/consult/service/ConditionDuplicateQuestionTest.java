package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConditionDuplicateQuestionTest {

    @Test
    @DisplayName("범위만 바꾼 질문은 같은 것으로 본다")
    void 범위만_바꾼_질문은_중복이다() {
        assertThat(ConditionGrounding.asksTheSame(
                "가입하려는 분의 나이가 만 14세 이상인가요?",
                "가입하려는 분의 나이가 만 14세 이상 18세 이하인가요?")).isTrue();
    }

    @Test
    @DisplayName("주어만 바꿔 같은 사실을 반대편에서 묻는 질문은 같은 것으로 본다")
    void 주어만_바꾼_질문은_중복이다() {
        assertThat(ConditionGrounding.asksTheSame(
                "대리인이 방문하시는 건가요?", "본인이 방문하시는 건가요?")).isTrue();
    }

    @Test
    @DisplayName("주어가 같거나 나머지 문장이 다르면 남긴다")
    void 주어_규칙은_나머지가_같을_때만_적용한다() {
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 방문하시는 건가요?", "본인이 신청하시는 건가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 직접 방문하시나요?", "대리인이 방문하시는 건가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 하시나요?", "대리인이 하시나요?")).isFalse();
    }

    @Test
    @DisplayName("본인과 대리인이 아닌 주어만 다른 질문은 서로 다른 조건으로 남긴다")
    void 배타적_짝이_아니면_남긴다() {
        assertThat(ConditionGrounding.asksTheSame(
                "양도인이 신분증을 지참하실 수 있나요?", "양수인이 신분증을 지참하실 수 있나요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "양도인이 미납 요금이 있나요?", "양수인이 미납 요금이 있나요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "할부금이 아직 남아 있나요?", "위약금이 아직 남아 있나요?")).isFalse();
    }

    @Test
    @DisplayName("나이 기준 질문의 대상을 구하고 합친 질문을 만든다")
    void 나이_기준_질문을_합친다() {
        assertThat(ConditionGrounding.ageSubject("만 14세 이상이신가요?")).isEqualTo("");
        assertThat(ConditionGrounding.ageSubject("본인이 만 65세 이상이신가요?")).isEqualTo("본인이");
        assertThat(ConditionGrounding.ageSubject("약정이 남아 있으신가요?")).isNull();
        assertThat(ConditionGrounding.mergedAgeQuestion("만 14세 이상이신가요?"))
                .isEqualTo("나이가 어떻게 되시나요?");
        assertThat(ConditionGrounding.mergedAgeQuestion("가입하려는 분의 나이가 만 18세 이하인가요?"))
                .isEqualTo("가입하려는 분의 나이가 어떻게 되시나요?");
        assertThat(ConditionGrounding.mergedAgeQuestion("본인이 만 65세 이상이신가요?"))
                .isEqualTo("본인의 나이가 어떻게 되시나요?");
    }

    @Test
    @DisplayName("나이 기준만 다른 질문은 중복으로 버리지 않는다")
    void 나이_기준만_다르면_버리지_않는다() {
        assertThat(ConditionGrounding.asksTheSame(
                "만 14세 이상이신가요?", "만 65세 이상이신가요?")).isFalse();
    }

    @Test
    @DisplayName("어미만 바꿔 묻는 질문도 같은 것으로 본다")
    void 어미만_바꾼_질문도_중복이다() {
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 직접 방문하시나요?",
                "본인이 직접 방문하시는 건가요?")).isTrue();
    }

    @Test
    @DisplayName("어미만 같고 묻는 대상이 다르면 남긴다")
    void 어미가_같아도_대상이_다르면_남긴다() {
        assertThat(ConditionGrounding.asksTheSame(
                "현재 미납 요금이 있으신가요?", "할부금이 있으신가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "첫 번째 질문인가요?", "두 번째 질문인가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 직접 방문하시나요?", "대리인이 방문하시는 건가요?")).isFalse();
    }

    @Test
    @DisplayName("서로 다른 조건은 남긴다")
    void 다른_조건은_남긴다() {
        assertThat(ConditionGrounding.asksTheSame(
                "현재 미납 요금이 있으신가요?",
                "본인이 직접 신청하시는 건가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "이번 달에 요금제를 변경하신 적 있나요?",
                "현재 미납 요금이 있으신가요?")).isFalse();
        assertThat(ConditionGrounding.asksTheSame(
                "본인이 만 65세 이상이신가요?",
                "가입하실 분의 나이가 만 18세 이하인가요?")).isFalse();
    }

    @Test
    @DisplayName("빈 질문은 중복으로 보지 않는다")
    void 빈_질문은_중복이_아니다() {
        assertThat(ConditionGrounding.asksTheSame("", "미납 요금이 있으신가요?")).isFalse();
    }

    @Test
    @DisplayName("고객이 질문에 쓴 말을 그대로 되묻는 것은 버린다")
    void 이미_말한_것은_되묻지_않는다() {
        assertThat(ConditionGrounding.alreadySaid(
                "일주일 여행하는데 로밍 무제한 할까요?", "로밍 무제한 요금제를 원하시나요?")).isTrue();
        assertThat(ConditionGrounding.alreadySaid(
                "명의변경할 때 할부금이 남아 있으면 어떻게 되나요",
                "명의변경을 원하시는 단말에 할부금이 남아 있나요?")).isTrue();
    }

    @Test
    @DisplayName("고객이 말하지 않은 조건은 묻는다")
    void 말하지_않은_조건은_묻는다() {
        assertThat(ConditionGrounding.alreadySaid(
                "번호이동 하고 싶어요", "현재 미납 요금이 있으신가요?")).isFalse();
        assertThat(ConditionGrounding.alreadySaid(
                "미성년자 가입되나요?", "가입하려는 분의 나이가 만 14세 이상인가요?")).isFalse();
        assertThat(ConditionGrounding.alreadySaid(
                "유심 재발급 받고 싶어요", "본인이 직접 재발급 받으시나요?")).isFalse();
        assertThat(ConditionGrounding.alreadySaid(
                "번호이동 했다가 다시 돌아갈 수 있나요", "번호이동을 하신 후 얼마나 지나셨나요?")).isFalse();
    }

    @Test
    @DisplayName("질문이 비면 버리지 않는다")
    void 빈_질문은_버리지_않는다() {
        assertThat(ConditionGrounding.alreadySaid("", "미납 요금이 있으신가요?")).isFalse();
        assertThat(ConditionGrounding.alreadySaid(null, "미납 요금이 있으신가요?")).isFalse();
    }

    @Test
    @DisplayName("모델이 이미 안다고 적은 사실을 되묻는 질문은 버린다")
    void 이미_안다고_적은_것은_되묻지_않는다() {
        assertThat(ConditionGrounding.alreadyKnown(
                java.util.List.of("개통한 지 8개월 됐다"), "개통 후 몇 개월이 지났나요?")).isTrue();
        assertThat(ConditionGrounding.alreadyKnown(
                java.util.List.of("개통한 지 두 달 됐다"), "개통 후 몇 개월이 지났나요?")).isTrue();
        assertThat(ConditionGrounding.alreadyKnown(
                java.util.List.of("개통한 지 4개월 됐다"), "명의 변경을 하셨나요?")).isFalse();
        assertThat(ConditionGrounding.alreadyKnown(java.util.List.of(), "명의 변경을 하셨나요?")).isFalse();
        assertThat(ConditionGrounding.alreadyKnown(
                java.util.List.of("로밍 요금제를 쓰고 있다"), "로밍 요금제 약정이 남아 있나요?")).isFalse();
    }
}
