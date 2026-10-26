package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.plan.entity.Plan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlanCatalogAnswererTest {

    @Autowired PlanCatalogAnswerer answerer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 요금제_이름이_있으면_그_요금제의_월정액과_데이터를_알려준다() {
        assertThat(answerer.answer("5G 스탠다드 얼마예요"))
                .contains("5G 스탠다드", "월정액 65,000원", "데이터 50GB")
                .doesNotContain("LTE", "알뜰");
        assertThat(answerer.answer("알뜰 미니가 얼마예요")).contains("월정액 15,000원", "데이터 1.5GB", "통화 300분", "문자 100건");
        assertThat(answerer.answer("5G 언리미티드 요금 알려줘")).contains("데이터 무제한", "통화 무제한", "문자 무제한");
    }

    @Test
    void 망_이름을_뗀_상품_이름과_여러_요금제_이름도_알아본다() {
        assertThat(answerer.answer("스탠다드 요금 알려줘")).contains("5G 스탠다드", "65,000원");
        assertThat(answerer.answer("5G 스탠다드랑 LTE 플러스 월 요금 알려줘"))
                .contains("5G 스탠다드", "65,000원", "LTE 플러스", "49,000원");
    }

    @Test
    void 청소년과_시니어는_이름이_겹쳐_상품_이름으로_알아보지_않는다() {
        assertThat(answerer.answer("청소년 요금제 있나요"))
                .contains("요금제 안내예요", "[5G]", "5G 청소년", "LTE 청소년", "알뜰 청소년")
                .doesNotContain("시니어", "5G 스탠다드");
        assertThat(answerer.answer("LTE 시니어 요금제 월 요금 알려줘"))
                .contains("LTE 시니어", "29,000원")
                .doesNotContain("5G 시니어", "알뜰 시니어");
    }

    @Test
    void 망으로_거르면_그_망의_요금제만_망_이름으로_묶어_보여준다() {
        String answer = answerer.answer("LTE 요금제 종류 알려줘");

        assertThat(answer).contains("[LTE]", "LTE 맥스", "LTE 플러스", "LTE 베이직", "LTE 청소년", "LTE 시니어")
                .doesNotContain("[5G]", "[알뜰]", "5G 스탠다드");
    }

    @Test
    void 청소년이나_시니어_요금제가_들어가면_가입_나이를_함께_알려준다() {
        assertThat(answerer.answer("청소년 요금제 있나요")).contains("청소년 요금제는 만 14~18세만 가입할 수 있어요");
        assertThat(answerer.answer("시니어 요금제 가격 알려주세요")).contains("시니어 요금제는 만 65세 이상만 가입할 수 있어요");
        assertThat(answerer.answer("요금제 뭐가 있어요"))
                .contains("청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요");
        assertThat(answerer.answer("5G 스탠다드 얼마예요")).doesNotContain("가입할 수 있어요");
    }

    @Test
    void 가장_싼_요금제와_가장_비싼_요금제는_일반_요금제_중에서_고른다() {
        assertThat(answerer.answer("LTE 제일 싼 요금제가 얼마예요"))
                .contains("일반 요금제 중 가장 저렴한", "LTE 베이직", "33,000원")
                .doesNotContain("LTE 시니어");
        assertThat(answerer.answer("가장 비싼 요금제는 뭐예요")).contains("가장 비싼", "5G 언리미티드", "105,000원");
        assertThat(answerer.answer("시니어 요금제 중 제일 저렴한 건 뭐예요"))
                .contains("알뜰 시니어", "18,000원").doesNotContain("일반 요금제 중");
    }

    @Test
    void 요금제_개수를_물으면_판매_중인_전체_종류를_알려준다() {
        assertThat(answerer.answer("요금제가 다 해서 몇 가지예요"))
                .contains("일반 요금제는 9종이에요. 청소년 3종, 시니어 3종은 따로 있어요.", "[5G]", "[LTE]", "[알뜰]");
    }

    @Test
    void 가격대와_가격_이하_조건으로_거른다() {
        String band = answerer.answer("3만 원대로 쓸 수 있는 요금제가 있나요");
        assertThat(band).contains("LTE 베이직", "33,000원", "LTE 청소년", "39,000원").doesNotContain("LTE 시니어", "5G 스탠다드");

        String under = answerer.answer("5만 원 안쪽 요금제 있나요");
        assertThat(under).contains("5G 라이트", "알뜰 미니").doesNotContain("5G 스탠다드", "LTE 맥스", "5G 프리미엄");
        assertThat(answerer.answer("2만 원 미만 요금제")).contains("알뜰 미니", "알뜰 시니어").doesNotContain("알뜰 청소년");
    }

    @Test
    void 데이터_무제한_요금제만_거른다() {
        assertThat(answerer.answer("데이터 무제한 요금제도 있나요")).contains("5G 언리미티드").doesNotContain("5G 프리미엄");
    }

    @Test
    void 조건에_맞는_요금제가_없으면_없다고_알리고_전체_요금제를_보여준다() {
        assertThat(answerer.answer("100만 원대 요금제"))
                .contains("조건에 맞는 요금제를 찾지 못했어요", "5G 언리미티드", "알뜰 시니어");
    }

    @Test
    void 조건을_말하지_않은_요금제_질문은_전체_요금제를_보여준다() {
        assertThat(answerer.answer("우리 요금제들 요금표 좀 보여주세요")).contains("요금제 안내", "5G 언리미티드", "알뜰 시니어");
    }

    @Test
    void 판매_종료된_요금제는_답변에서_빠지고_판매_중인_요금제가_없으면_답하지_않는다() {
        jdbc.update("UPDATE plans SET status = 'DISCONTINUED' WHERE code = 'LTE_BASIC'");

        assertThat(answerer.answer("LTE 요금제 종류 알려줘")).doesNotContain("LTE 베이직").contains("LTE 맥스");
        assertThat(answerer.answer("LTE 베이직 얼마예요")).doesNotContain("LTE 베이직");

        jdbc.update("UPDATE plans SET status = 'DISCONTINUED'");
        assertThat(answerer.answer("요금제 뭐가 있어요")).isNull();
    }

    // 분류 모델이 고른 요금제가 있으면 질문 속 낱말로 찾지 않는다("유튜브 프리미엄"의 '프리미엄'을 5G 프리미엄으로 읽지 않는다)
    @Test
    void 모델이_고른_요금제만_상세로_알려준다() {
        String answer = answerer.answer("5G 스탠다드랑 프리미엄 중 하나요", new PlanQuestion.Mention(List.of("FIVE_G_STANDARD"), false));
        assertThat(answer).contains("5G 스탠다드", "65,000원").doesNotContain("5G 프리미엄");

        assertThat(answerer.answer("유튜브 프리미엄 포함된 요금제 있어요", new PlanQuestion.Mention(List.of(), false)))
                .doesNotContain("월정액 85,000원");
    }

    @Test
    void 목록에_없는_요금제는_없다고_말하고_전체_목록을_보여준다() {
        assertThat(answerer.answer("5G 프리미엄 플러스 얼마예요", new PlanQuestion.Mention(List.of(), true)))
                .startsWith("말씀하신 요금제는 판매 중인 요금제에 없어요")
                .contains("5G 프리미엄", "LTE 플러스");
    }

    // FAQ와 같은 기준(일반 라인업)으로 세고 연령 요금제는 따로 알린다
    @Test
    void 개수는_일반_라인업으로_세고_청소년_시니어는_따로_알린다() {
        assertThat(answerer.answer("LTE 요금제는 몇 개예요"))
                .startsWith("일반 LTE 요금제는 3종이에요. 청소년 1종, 시니어 1종은 따로 있어요.");
        assertThat(answerer.answer("요금제 몇 종류 있어요")).startsWith("일반 요금제는 9종이에요. 청소년 3종, 시니어 3종은 따로 있어요.");
        assertThat(answerer.answer("시니어 요금제 몇 개예요")).startsWith("판매 중인 요금제는 총 3종이에요.");
    }

    // 나이 답에서 쓰는 대상 낱말(어르신, 고등학생 등)도 대상 조건으로 읽는다
    @Test
    void 청소년_시니어를_다른_말로_불러도_그_대상으로_거른다() {
        assertThat(answerer.answer("어르신 전용 요금제는 몇 종류예요")).startsWith("판매 중인 요금제는 총 3종이에요.");
        assertThat(answerer.answer("고등학생이 쓸 요금제 알려줘")).contains("5G 청소년", "LTE 청소년").doesNotContain("5G 스탠다드");
    }

    // 요금제 이름이나 망 이름을 뗀 이름 뒤의 제외 표현은 그 요금제만 뺀다(망 전체를 빼지 않는다)
    // 모델이 뺀다고 한 요금제를 골라도 상세로 보여주지 않고 남은 조건으로 거른다(리뷰)
    @Test
    void 모델이_고른_요금제라도_뺀다고_한_것은_보여주지_않는다() {
        String answer = answerer.answer("프리미엄 빼고 5G 요금제 알려줘", new PlanQuestion.Mention(List.of("FIVE_G_PREMIUM"), false));
        assertThat(answer).contains("5G 스탠다드").doesNotContain("5G 프리미엄");
    }

    @Test
    void 이름으로_뺀_요금제만_목록에서_제외한다() {
        assertThat(answerer.answer("LTE 맥스 제외하고 LTE 요금제 목록 보여주세요")).contains("LTE 플러스", "LTE 베이직").doesNotContain("LTE 맥스");
        assertThat(answerer.answer("프리미엄 빼고 5G 요금제 알려줘")).contains("5G 스탠다드").doesNotContain("5G 프리미엄");
        assertThat(answerer.answer("알뜰 미니는 필요 없고 다른 알뜰 요금제 뭐 있어요")).contains("알뜰 세이브").doesNotContain("알뜰 미니");
        assertThat(answerer.answer("알뜰은 필요없고 요금제 목록 보여줘")).contains("5G 스탠다드").doesNotContain("알뜰 세이브");
    }

    @Test
    void 말고_빼고로_뺀_대상은_목록에서_제외한다() {
        String general = answerer.answer("청소년이나 시니어 말고 일반 요금제는 몇 종류예요");
        assertThat(general).contains("총 9종").doesNotContain("청소년", "시니어");

        assertThat(answerer.answer("5G 말고 LTE 요금제 알려줘")).contains("LTE 맥스").doesNotContain("5G 스탠다드");
        assertThat(answerer.answer("시니어 요금제 빼고 제일 싼 요금제")).contains("알뜰 미니").doesNotContain("알뜰 시니어");
    }

    // 모델이 읽은 목록 조건이 있으면 질문의 낱말 대신 그것으로 거른다. 이유로 꺼낸 망·대상 낱말은 조건이 아니다(TELME-227)
    @Test
    void 모델이_읽은_목록_조건이_있으면_낱말_대신_그_조건으로_거른다() {
        PlanQuestion.Conditions lte = conditions(List.of(Plan.NetworkType.LTE), List.of(), List.of(), List.of(),
                PlanQuestion.Unlimited.ANY, List.of());
        assertThat(answerer.answer("LTE 요금제 몇개에요? 5G는 안터지는 동네라서요", listMention(lte)))
                .startsWith("일반 LTE 요금제는 3종이에요. 청소년 1종, 시니어 1종은 따로 있어요.")
                .doesNotContain("5G 스탠다드");

        PlanQuestion.Conditions noBudget = conditions(List.of(), List.of(Plan.NetworkType.BUDGET), List.of(), List.of(),
                PlanQuestion.Unlimited.ANY, List.of());
        assertThat(answerer.answer("알뜰 같은 건 됐고요 요금제 종류 쭉 알려주실래요?", listMention(noBudget)))
                .contains("5G 스탠다드", "LTE 맥스").doesNotContain("알뜰 세이브", "알뜰 미니");

        PlanQuestion.Conditions fiveGWithoutUnlimitedPlan = conditions(List.of(Plan.NetworkType.FIVE_G), List.of(), List.of(),
                List.of(), PlanQuestion.Unlimited.ANY, List.of("FIVE_G_UNLIMITED"));
        assertThat(answerer.answer("무제한은 너무 비싸서요 5G 언리미티드 아닌 걸로 5G 요금제 뭐 있어요?", listMention(fiveGWithoutUnlimitedPlan)))
                .contains("5G 프리미엄", "5G 스탠다드").doesNotContain("5G 언리미티드", "LTE 맥스");
    }

    @Test
    void 모델_조건으로_무제한을_빼거나_대상을_고르고_대상을_물었는지도_조건으로_본다() {
        PlanQuestion.Conditions limited = conditions(List.of(Plan.NetworkType.FIVE_G), List.of(), List.of(), List.of(),
                PlanQuestion.Unlimited.EXCLUDE, List.of());
        assertThat(answerer.answer("무제한은 필요없어요 5G 요금제 보여줘", listMention(limited)))
                .contains("5G 프리미엄", "5G 라이트").doesNotContain("5G 언리미티드");

        PlanQuestion.Conditions youth = conditions(List.of(), List.of(), List.of(Plan.TargetGroup.YOUTH), List.of(),
                PlanQuestion.Unlimited.ANY, List.of());
        assertThat(answerer.answer("엄마는 5G 시니어 쓰시고 저는 고딩인데 청소년 요금제 몇개 있어요?", listMention(youth)))
                .startsWith("판매 중인 요금제는 총 3종이에요.").doesNotContain("시니어");

        // 이유로 꺼낸 "시니어"는 대상을 물은 것이 아니라 일반 라인업으로 센다
        PlanQuestion.Conditions any = conditions(List.of(), List.of(), List.of(), List.of(), PlanQuestion.Unlimited.ANY, List.of());
        assertThat(answerer.answer("엄마 시니어 요금제 내드리느라 부담돼요 요금제 전체 몇 개예요?", listMention(any)))
                .startsWith("일반 요금제는 9종이에요.");
        assertThat(answerer.answer("시니어 요금제 쓰는 엄마 대신 묻는데 제일 싼 요금제 뭐예요", listMention(any)))
                .startsWith("일반 요금제 중 가장 저렴한 요금제예요.").contains("알뜰 미니");
    }

    private static PlanQuestion.Mention listMention(PlanQuestion.Conditions conditions) {
        return new PlanQuestion.Mention(List.of(), false, conditions);
    }

    private static PlanQuestion.Conditions conditions(List<Plan.NetworkType> networks, List<Plan.NetworkType> excludedNetworks,
            List<Plan.TargetGroup> groups, List<Plan.TargetGroup> excludedGroups, PlanQuestion.Unlimited unlimited,
            List<String> excludedPlans) {
        return new PlanQuestion.Conditions(networks, excludedNetworks, groups, excludedGroups, unlimited, excludedPlans);
    }

    @Test
    void 터무니없이_큰_가격_숫자도_오류_없이_조건_없는_질문처럼_처리한다() {
        for (String question : new String[] {"99999999999만원대 요금제", "2147483648만원이하 요금제 알려줘", "9".repeat(3000) + "만원대 요금제"}) {
            assertThat(answerer.answer(question)).contains("요금제 안내예요");
        }
    }
}
