package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanQuestionRulesTest {

    @Test
    void 요금제_추천_질문() {
        assertType(PlanQuestionType.RECOMMENDATION,
                "요금제 추천해줘", "나한테 맞는 요금제 알려줘", "어떤 요금제가 좋을까요",
                "데이터 많이 쓰는데 요금제 추천 부탁해요", "요금제 뭐가 나한테 어울려요",
                "지금 쓰는 요금제로는 데이터가 부족한데 어떤 요금제로 갈아타야 할지 모르겠습니다");
    }

    @Test
    void 내_요금제_질문() {
        assertType(PlanQuestionType.MY_SUBSCRIPTION,
                "내 요금제 뭐야", "지금 쓰는 요금제 알려줘", "내가 가입한 요금제 확인해줘",
                "현재 요금제 얼마야", "제 요금제가 뭔가요");
    }

    @Test
    void 내_위약금과_약정_질문() {
        assertType(PlanQuestionType.MY_PENALTY,
                "지금 해지하면 위약금 얼마예요", "지금 해지하면 위약금 얼마 나와요", "내 위약금 알려줘",
                "내 약정 언제 끝나요", "제 약정 확인해주세요");
    }

    @Test
    void 내_사용량과_예상_요금_질문() {
        assertType(PlanQuestionType.MY_USAGE,
                "이번 달 사용량 알려줘", "내 데이터 얼마나 썼어", "남은 데이터 얼마야", "이번 달 요금 얼마 나와",
                "통화 얼마나 썼는지 알려줘", "내 사용량 확인", "이번 달 예상 요금 알려줘");
    }

    @Test
    void 요금제_목록_질문() {
        assertType(PlanQuestionType.PLAN_CATALOG,
                "5G 요금제 종류 알려줘", "요금제 뭐가 있어요", "청소년 요금제 있나요",
                "제일 싼 요금제 얼마예요", "시니어 요금제 가격 알려줘");
    }

    @Test
    void 일반_정책_질문과_다른_주제는_기존_경로로_보낸다() {
        assertType(PlanQuestionType.NONE,
                "안녕하세요", "해지하면 위약금 있어요?", "약정이 뭐예요", "요금제 변경은 어떻게 해요",
                "신촌 매장 알려줘", "데이터 이월되나요", "로밍 요금 얼마예요", "남은 데이터 다음 달에 쓸 수 있어요",
                "위약금 얼마 내야 하는지 어떻게 알아요", "손주 요금제랑 내 요금제랑 뭐가 달라요",
                "데이터 사용량 기준으로 요금제 고르는 절차를 알려주세요", "5G 스탠다드 월정액과 기본 데이터를 알려주세요");
        assertThat(PlanQuestionRules.classify(null)).isEqualTo(PlanQuestionType.NONE);
        assertThat(PlanQuestionRules.classify("   ")).isEqualTo(PlanQuestionType.NONE);
    }

    @Test
    void 본인을_가리키는_표현이_있으면_내_정보_질문으로_인정한다() {
        assertMarker(PlanQuestionType.MY_SUBSCRIPTION, true,
                "내 요금제 뭐야", "내가 가입한 요금제 확인해줘", "나 어떤 요금제야", "제가 쓰는 요금제 알려줘",
                "지금 무슨 요금제 쓰고 있죠",
                "현재 가입돼 있는 요금제가 뭔지 알려줘요", "현재 요금제 이름이랑 월 요금 알려줘");
        assertMarker(PlanQuestionType.MY_PENALTY, true,
                "저 약정 몇 개월 남았어요", "지금 해지하면 얼마 내요", "약정 남은 기간 알려줘", "오늘 해지하면 위약금 내야 해요");
        assertMarker(PlanQuestionType.MY_USAGE, true,
                "이번 달 데이터 얼마나 썼어", "데이터 얼마 남았어요", "남은 데이터 알려줘", "문자 몇 건 보냈는지 궁금해요");
    }

    @Test
    void 본인을_가리키는_표현이_없거나_단어_일부만_겹치면_인정하지_않는다() {
        assertMarker(PlanQuestionType.MY_PENALTY, false,
                "폰 잃어버려서 해지하면 위약금 있어요", "약정 중간에 해지하면 위약금이 얼마나 나오나요",
                "해지하면 남은 할부금은 어떻게 됩니까", "내일 해지하면 위약금이 있나요", "나이가 바뀌면 위약금이 달라요",
                "제일 큰 위약금이 얼마예요", "제도가 바뀌었나요", "할부금을 한 번에 내는 게 나아요",
                "지금 해지하는 거랑 나중에 하는 거랑 위약금이 달라요",
                "지금 해지하는 것과 약정을 채우는 것 중 위약금 차이가 큰가요",
                "미납 요금을 먼저 내고 명의변경하는 것과 명의변경 후에 내는 것 중 어느 쪽인가요");
        assertMarker(PlanQuestionType.MY_USAGE, false, "데이터를 다 쓰면 어떻게 돼요", "데이터 이월은 어떻게 해요",
                "안 쓴 데이터가 다음 달로 얼마나 넘어가요", "데이터를 다 쓴 뒤 속도 제한을 피하려면 어떻게 해야 하나요");
        assertMarker(PlanQuestionType.MY_PENALTY, false, "부가서비스를 가입한 당일에 해지해도 그 달 요금이 나오나요");
        assertMarker(PlanQuestionType.MY_SUBSCRIPTION, false, "요금제 바꾸는 방법 알려주세요");
    }

    // 규칙을 바꿔 목록이 달라지면 FAQ를 가로채지 않는지 확인한 뒤 고친다
    @Test
    void 기존_FAQ_질문은_요금제_목록과_추천_질문만_가져간다() throws Exception {
        JsonNode faqs = new ObjectMapper().readTree(new File("scripts/data/faq_full_1150.json"));
        assertThat(faqs).hasSize(1150);

        Map<String, PlanQuestionType> captured = new LinkedHashMap<>();
        for (JsonNode faq : faqs) {
            String question = faq.get("question").asText();
            PlanQuestionType type = PlanQuestionRules.classify(question);
            if (type != PlanQuestionType.NONE) {
                captured.put(question, type);
            }
        }

        assertThat(captured).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("요금제 몇 종류나 있어요? 이름 말고 그냥 개수만요", PlanQuestionType.PLAN_CATALOG),
                Map.entry("데이터 무제한 요금제도 있나요", PlanQuestionType.PLAN_CATALOG),
                Map.entry("데이터 많이 주는 5G 요금제는 몇 개나 있어요?", PlanQuestionType.PLAN_CATALOG),
                Map.entry("요금 아끼려는데 알뜰 요금제는 뭐가 있어요?", PlanQuestionType.PLAN_CATALOG),
                Map.entry("LTE 제일 싼 요금제가 얼마예요?", PlanQuestionType.PLAN_CATALOG),
                Map.entry("요금제가 다 해서 몇 가지예요", PlanQuestionType.PLAN_CATALOG),
                Map.entry("LTE 요금제 얼마씩 해요", PlanQuestionType.PLAN_CATALOG),
                Map.entry("3만 원대로 쓸 수 있는 요금제가 있나요?", PlanQuestionType.PLAN_CATALOG),
                Map.entry("5만 원 안쪽 요금제 있나요", PlanQuestionType.PLAN_CATALOG),
                Map.entry("지금 쓰는 요금제로는 데이터가 부족한데 어떤 요금제로 갈아타야 할지 모르겠습니다",
                        PlanQuestionType.RECOMMENDATION)));
    }

    private void assertMarker(PlanQuestionType type, boolean expected, String... questions) {
        for (String question : questions) {
            assertThat(PlanQuestionRules.hasPersonalMarker(type, question)).as(question).isEqualTo(expected);
        }
    }

    private void assertType(PlanQuestionType expected, String... questions) {
        for (String question : List.of(questions)) {
            assertThat(PlanQuestionRules.classify(question)).as(question).isEqualTo(expected);
        }
    }
}
