package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanTaskRulesTest {

    @ParameterizedTest
    @DisplayName("요금제 이름이나 \"그걸로\"와 함께 바꿔 달라는 말, \"요금제 바꿔줘\"처럼 분명히 맡기는 말은 변경 요청이다")
    @ValueSource(strings = {
            "5G 라이트로 바꿔줘", "LTE 맥스로 변경해 주세요", "5G 프리미엄으로 바꿔줘", "알뜰 미니로 갈아탈래요",
            "5G 스탠다드로 변경 신청할게요", "요금제 바꿔줘", "그걸로 바꿔줘", "추천해준 요금제로 변경해주세요", "휴대폰 요금제 바꿔줘", "요금제 변경 부탁드려요",
            "오지 라이트로 바꿔 주세요", "5G 라이트로 바꾸고 싶어요", "lte 베이직으로 할게요"})
    void 변경(String question) {
        assertThat(PlanTaskRules.detect(question)).contains(PlanTask.CHANGE);
    }

    @ParameterizedTest
    @DisplayName("신청·가입 요청은 변경이 아니라 신규 신청이다")
    @ValueSource(strings = {
            "요금제 신청해줘", "5G 라이트 신청해줘", "5G 라이트 가입하고 싶어요", "LTE 맥스 가입할게요",
            "5G 라이트 약정 없이 신청해줘", "5G 라이트 24개월 약정으로 신청해줘", "알뜰 미니로 신청할게요"})
    void 신청(String question) {
        assertThat(PlanTaskRules.detect(question)).contains(PlanTask.SUBSCRIBE);
    }

    @ParameterizedTest
    @DisplayName("일시 정지 요청")
    @ValueSource(strings = {"일시정지해줘", "일시 정지해 주세요", "7일 일시 정지해줘", "요금제 일시 정지 신청할게요",
            "한 달 동안 일시정지 할게요", "요금제 정지해줘", "15일 정지해줘"})
    void 일시_정지(String question) {
        assertThat(PlanTaskRules.detect(question)).contains(PlanTask.PAUSE);
    }

    @ParameterizedTest
    @DisplayName("해지 요청")
    @ValueSource(strings = {"해지해줘", "요금제 해지해 주세요", "요금제 해지할게요",
            "요금제 해지하고 싶어요"})
    void 해지(String question) {
        assertThat(PlanTaskRules.detect(question)).contains(PlanTask.CANCEL);
    }

    @ParameterizedTest
    @DisplayName("방법·가능 여부·가정·비교·고르는 중인 말, 요금제가 아닌 업무는 요금제 업무 요청이 아니다")
    @ValueSource(strings = {
            "요금제 변경은 어떻게 해요", "5G 라이트로 바꿀 수 있어요?", "LTE 맥스로 바꾸면 얼마예요?",
            "어떤 요금제로 바꿔야 할까요", "요금제 바꾸고 싶은데 뭐가 좋을까요", "5G 라이트랑 LTE 맥스 차이가 뭐예요",
            "로밍 요금제로 바꿔줘", "요금제 추천해줘", "5G 라이트 가입 조건이 뭐예요", "요금제 바꾸고 싶어요",
            "요금제 변경 방법 알려줘", "요금제 변경 신청일과 실제 적용일이 같은가요?",
            "알뜰폰으로 바꾸고 싶어요", "기기 변경해 주세요", "번호 바꿔줘", "유심 변경해줘", "명의 변경 신청할게요",
            "신청해줘", "가입하려면 뭐가 필요해요?", "해지하면 위약금 얼마예요?", "해지 방법 알려줘", "해지하고 싶어요",
            "일시정지는 어떻게 해요?", "정지 해제해줘", "일시 정지 풀어줘", "분실 정지해줘", "부가서비스 해지해줘",
            "회원 탈퇴해줘", "회원가입 하고 싶어요",
            "디즈니 플러스 해지해줘", "유튜브 프리미엄 가입해줘", "넷플릭스 베이직으로 바꿔줘", "티빙 해지할게요",
            "요금제 변경 신청 취소해줘", "요금제 가입 신청 취소해줘", "요금제 해지 신청 취소해줘", "5G 라이트로 바꾼 거 취소해줘",
            "일시 정지 신청 철회할게요",
            "5G 라이트 가입 혜택 정리해줘", "요금제 변경 이유 설명해줘", "요금제 변경 가능한지 확인해줘", "해지 위약금 계산해줘",
            "인터넷 해지해줘", "IPTV 해지해줘", "휴대폰 바꿔줘", "핸드폰 5G로 바꾸고 싶어요"})
    void 업무_요청_아님(String question) {
        assertThat(PlanTaskRules.detect(question)).isEmpty();
    }

    // 업무 요청은 FAQ 앞에서 처리되므로 기존 FAQ 질문을 하나라도 가져가면 실패한다
    @Test
    @DisplayName("기존 FAQ 1,150개 질문은 하나도 요금제 업무 요청으로 가져가지 않는다")
    void 기존_FAQ_질문() throws Exception {
        JsonNode faqs = new ObjectMapper().readTree(new File("scripts/data/faq_full_1150.json"));
        assertThat(faqs).hasSize(1150);

        Map<String, PlanTask> captured = new LinkedHashMap<>();
        for (JsonNode faq : faqs) {
            String question = faq.get("question").asText();
            PlanTaskRules.detect(question).ifPresent(task -> captured.put(question, task));
        }

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("처리 화면 링크 문구는 업무 요청으로 다시 들어오지 않고 화면 안내로 받는다")
    void 링크_문구() {
        for (String link : List.of(PlanTaskLinks.SUBSCRIBE, PlanTaskLinks.CHANGE, PlanTaskLinks.PAUSE, PlanTaskLinks.CANCEL)) {
            assertThat(PlanTaskRules.detect(link)).isEmpty();
            assertThat(PlanTaskLinks.guideFor(link)).isPresent();
        }
        assertThat(PlanTaskLinks.guideFor("요금제 해지해줘")).isEmpty();
    }

    @Test
    @DisplayName("정지 기간은 숫자 일수, 주, 보름, 달을 일수로 읽는다")
    void 정지_기간() {
        assertThat(PlanTaskRules.pauseDays("10일 일시정지해줘")).hasValue(10);
        assertThat(PlanTaskRules.pauseDays("일주일 일시정지해줘")).hasValue(7);
        assertThat(PlanTaskRules.pauseDays("2주 일시정지해줘")).hasValue(14);
        assertThat(PlanTaskRules.pauseDays("보름 일시정지해줘")).hasValue(15);
        assertThat(PlanTaskRules.pauseDays("한 달 일시정지해줘")).hasValue(30);
        assertThat(PlanTaskRules.pauseDays("두 달 일시정지해줘")).hasValue(60);
        assertThat(PlanTaskRules.pauseDays("일시정지해줘")).isEqualTo(OptionalInt.empty());
        // 범위로 말하면 첫 숫자를 기간으로 읽지 않고 화면에서 고르게 한다. 정지 요청으로는 받는다
        assertThat(PlanTaskRules.pauseDays("3일부터 7일까지 일시정지해줘")).isEqualTo(OptionalInt.empty());
        assertThat(PlanTaskRules.pauseDays("3~7일 일시정지해줘")).isEqualTo(OptionalInt.empty());
        assertThat(PlanTaskRules.pauseDays("10월 20일부터 25일까지 일시정지해줘")).isEqualTo(OptionalInt.empty());
        assertThat(PlanTaskRules.pauseDays("오늘부터 7일 일시정지해줘")).hasValue(7);
        assertThat(PlanTaskRules.detect("3일부터 7일까지 일시정지해줘")).contains(PlanTask.PAUSE);
    }

    @Test
    @DisplayName("안내 문구는 받침에 맞춰 로/으로, 은/는, 을/를을 붙인다")
    void 조사() {
        assertThat(PlanTaskSupport.directionParticle("5G 스탠다드")).isEqualTo("로");
        assertThat(PlanTaskSupport.directionParticle("5G 프리미엄")).isEqualTo("으로");
        assertThat(PlanTaskSupport.directionParticle("LTE 플러스")).isEqualTo("로");
        assertThat(PlanTaskSupport.directionParticle("5G")).isEqualTo("로");
        assertThat(PlanTaskSupport.topicParticle("LTE 맥스")).isEqualTo("는");
        assertThat(PlanTaskSupport.topicParticle("5G 청소년")).isEqualTo("은");
        assertThat(PlanTaskSupport.objectParticle("LTE 맥스")).isEqualTo("를");
        assertThat(PlanTaskSupport.objectParticle("LTE 베이직")).isEqualTo("을");
    }
}
