package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanChangeRequestRulesTest {

    @ParameterizedTest
    @DisplayName("요금제 이름이나 \"그걸로\"와 함께 바꿔 달라는 말, 분명히 맡기는 말은 변경 요청이다")
    @ValueSource(strings = {
            "5G 라이트로 바꿔줘", "LTE 맥스로 변경해 주세요", "5G 프리미엄으로 바꿔줘", "알뜰 미니로 갈아탈래요",
            "5G 스탠다드로 변경 신청할게요", "요금제 바꿔줘", "그걸로 바꿔줘", "추천해준 요금제로 변경해주세요",
            "오지 라이트로 바꿔 주세요", "5G 라이트로 바꾸고 싶어요", "lte 베이직으로 할게요"})
    void 변경_요청(String question) {
        assertThat(PlanChangeRequestRules.requestsPlanChange(question)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("방법·가능 여부·가정·비교·고르는 중인 말과 다른 서비스 요금은 변경 요청이 아니다")
    @ValueSource(strings = {
            "요금제 변경은 어떻게 해요", "5G 라이트로 바꿀 수 있어요?", "LTE 맥스로 바꾸면 얼마예요?",
            "어떤 요금제로 바꿔야 할까요", "요금제 바꾸고 싶은데 뭐가 좋을까요", "5G 라이트랑 LTE 맥스 차이가 뭐예요",
            "로밍 요금제로 바꿔줘", "요금제 추천해줘", "5G 라이트 가입 조건이 뭐예요", "요금제 바꾸고 싶어요",
            "요금제 변경 방법 알려줘", "요금제 변경 신청일과 실제 적용일이 같은가요?",
            "알뜰폰으로 바꾸고 싶어요", "기기 변경해 주세요", "번호 바꿔줘", "유심 변경해줘", "명의 변경 신청할게요"})
    void 변경_요청_아님(String question) {
        assertThat(PlanChangeRequestRules.requestsPlanChange(question)).isFalse();
    }

    // 규칙을 바꿔 FAQ 질문을 가로채게 되면 실패한다. 변경 요청은 FAQ 앞에서 처리되므로 기존 FAQ 질문은 하나도 가져가지 않아야 한다
    @Test
    @DisplayName("기존 FAQ 1,150개 질문은 하나도 변경 요청으로 가져가지 않는다")
    void 기존_FAQ_질문() throws Exception {
        JsonNode faqs = new ObjectMapper().readTree(new File("scripts/data/faq_full_1150.json"));
        assertThat(faqs).hasSize(1150);

        List<String> captured = new ArrayList<>();
        for (JsonNode faq : faqs) {
            String question = faq.get("question").asText();
            if (PlanChangeRequestRules.requestsPlanChange(question)) {
                captured.add(question);
            }
        }

        assertThat(captured).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("추천 답변 아래 변경 버튼 문구는 모든 요금제 이름에서 변경 요청으로 분류된다")
    @ValueSource(strings = {
            "5G 언리미티드", "5G 프리미엄", "5G 스탠다드", "5G 라이트", "LTE 맥스", "LTE 플러스", "LTE 베이직",
            "알뜰 세이브", "알뜰 미니", "5G 청소년", "LTE 청소년", "알뜰 청소년", "5G 시니어", "LTE 시니어", "알뜰 시니어"})
    void 버튼_문구(String planName) {
        assertThat(PlanChangeRequestRules.requestsPlanChange(PlanChangeAnswerer.changeRequest(planName))).isTrue();
    }

    @Test
    @DisplayName("버튼 문구는 받침에 맞춰 로/으로를 붙인다")
    void 조사() {
        assertThat(PlanChangeAnswerer.changeRequest("5G 스탠다드")).isEqualTo("5G 스탠다드로 바꿔줘");
        assertThat(PlanChangeAnswerer.changeRequest("5G 프리미엄")).isEqualTo("5G 프리미엄으로 바꿔줘");
        assertThat(PlanChangeAnswerer.changeRequest("LTE 플러스")).isEqualTo("LTE 플러스로 바꿔줘");
        assertThat(PlanChangeAnswerer.changeRequest("알뜰 세이브")).isEqualTo("알뜰 세이브로 바꿔줘");
        assertThat(PlanChangeAnswerer.changeRequest("5G")).isEqualTo("5G로 바꿔줘");
    }
}
