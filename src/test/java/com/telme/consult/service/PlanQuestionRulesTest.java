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
    void 방법_자격_가능_여부_뜻_비교를_묻는_질문은_요금제_목록과_추천이_아니다() {
        assertProcedure(PlanQuestionType.PLAN_CATALOG, true,
                "시니어 요금제로 바꾸려면 어떻게 해요", "제일 싼 요금제로 가입할 수 있을까요", "알뜰 요금제로 5G를 쓸 수 있어요",
                "청소년 요금제는 몇 살까지 쓸 수 있어요", "알뜰 요금제라는 게 뭐예요", "요금제 뭐가 있는지 어떻게 알아요",
                "5G 요금제랑 알뜰 요금제 중 뭐가 싸요", "싼 거랑 비싼 거랑 얼마 차이예요",
                "가족 모두 5G 언리미티드로 바꿀 수 있나요");
        assertProcedure(PlanQuestionType.RECOMMENDATION, true,
                "요금제를 고르는 방법 알려주세요", "식구들 요금제 어떻게 골라요", "5G 라이트랑 LTE 플러스 중 뭐가 나아요",
                "요금이 많이 나와서 요금제를 낮추려면 어떻게 해요");
    }

    @Test
    void 목록을_묻거나_추천을_바라는_질문은_막지_않는다() {
        assertProcedure(PlanQuestionType.PLAN_CATALOG, false,
                "요금제 뭐가 있어요", "5G 스탠다드 얼마예요", "LTE 요금제 이름이 뭐뭐예요", "알뜰 미니가 얼마예요",
                "요금제가 다 해서 몇 가지예요", "시니어 요금제 월 요금이 얼마예요", "제일 저렴한 요금제가 뭐예요",
                "가장 비싼 요금제는 뭐예요", "LTE 요금제 종류가 어떻게 돼요", "청소년이 쓸 수 있는 요금제는 뭐가 있나요");
        assertProcedure(PlanQuestionType.RECOMMENDATION, false,
                "요금제 추천해줘", "저한테 맞는 요금제 있을까요", "데이터 많이 쓰는데 어떤 요금제가 좋아요",
                "어떤 요금제로 갈아타야 할지 모르겠습니다", "데이터를 아끼려면 어떤 요금제가 좋아요",
                "영상을 많이 보는데 어떤 요금제를 쓰면 될까요", "제가 쓰기에 가성비 좋은 요금제가 뭐예요",
                "지금 요금제보다 더 나은 게 있을까요", "지금보다 저렴하게 쓰려면 어떤 걸로 옮기는 게 나아요");
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

    @Test
    void 통화_문자_데이터를_얼마나_쓸_수_있는지는_제공량_질문이고_다른_가능_여부는_절차_질문이다() {
        assertProcedure(PlanQuestionType.PLAN_CATALOG, false, "5G 프리미엄은 통화를 얼마나 쓸 수 있죠",
                "알뜰 세이브는 문자를 몇 건 보낼 수 있나요", "데이터는 얼마나 쓸 수 있어요");
        assertProcedure(PlanQuestionType.PLAN_CATALOG, true, "요금제를 바꿀 수 있나요", "청소년 요금제에 가입할 수 있어요",
                "통화 품질을 개선할 수 있나요 얼마나 걸려요");
    }

    @Test
    void 방법_자격_비교_로밍_질문은_요금제_답변이_아니라_FAQ가_답한다() {
        assertProcedure(PlanQuestionType.PLAN_CATALOG, true, "요금제 표 보는 법 좀 알려주세요",
                "시니어 요금제와 알뜰 요금제 중 요금 절감 효과가 더 큰 쪽은 어디인가요");
        assertProcedure(PlanQuestionType.RECOMMENDATION, true, "할머니는 시니어 요금제 되고 저는 청소년 요금제 되는 거예요?");
        assertProcedure(PlanQuestionType.MY_PENALTY, false, "지금 당장 해지하면 위약금이 얼마나 되나요");
        assertProcedure(PlanQuestionType.MY_USAGE, true, "데이터 다 썼다고 문자 오면 어떻게 해요");
        assertProcedure(PlanQuestionType.MY_USAGE, false, "이번 달 데이터 얼마나 썼어요", "내 통화 사용량 알려줘");
        assertProcedure(PlanQuestionType.PLAN_CATALOG, false, "5G 스탠다드 얼마예요", "요금제 종류 알려줘");
        assertThat(PlanQuestionRules.asksOtherService("해외 나갈 때 데이터 하루에 얼마예요")).isTrue();
        assertThat(PlanQuestionRules.asksOtherService("보름 동안 로밍을 썼는데 요금이 많이 나왔어요")).isTrue();
        assertThat(PlanQuestionRules.asksOtherService("5G 요금제 종류 알려줘")).isFalse();
        assertType(PlanQuestionType.NONE, "로밍 요금제 종류 알려줘", "해외에서 쓸 요금제 추천해줘");
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

    private void assertProcedure(PlanQuestionType type, boolean expected, String... questions) {
        for (String question : questions) {
            assertThat(PlanQuestionRules.asksProcedure(type, question)).as(question).isEqualTo(expected);
        }
    }

    private void assertType(PlanQuestionType expected, String... questions) {
        for (String question : List.of(questions)) {
            assertThat(PlanQuestionRules.classify(question)).as(question).isEqualTo(expected);
        }
    }

    @Test
    void 하루_단위_요금_질문만_요금제_표_밖의_질문으로_본다() {
        for (String question : new String[] {"요금은 하루에 얼마정도에요", "하루 요금이 얼마예요", "일일 비용이 얼마나 나와요", "1일 요금이 얼마예요"}) {
            assertThat(PlanQuestionRules.asksDailyFee(question)).as(question).isTrue();
        }
        for (String question : new String[] {"약정 끝나기 1일 남았는데 위약금 얼마예요", "11일에 개통했는데 이번 달 요금 얼마 나와요",
                "매달 21일에 빠져나가는 금액 얼마예요", "하루 2GB 주는 요금제 뭐 있어요", "5G 스탠다드 얼마예요", "이번 달 요금 얼마예요"}) {
            assertThat(PlanQuestionRules.asksDailyFee(question)).as(question).isFalse();
        }
    }
}
