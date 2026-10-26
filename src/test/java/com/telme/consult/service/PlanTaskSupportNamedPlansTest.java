package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.plan.entity.Plan;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanTaskSupportNamedPlansTest {

    private static final Plan LITE = plan(1L, "5G 라이트");
    private static final Plan STANDARD = plan(2L, "5G 스탠다드");
    private static final Plan MAX = plan(3L, "LTE 맥스");
    private static final List<Plan> SELLING = List.of(LITE, STANDARD, MAX);

    private static final String RECOMMENDED = "한 달에 데이터를 40GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요.";
    // 문맥으로 풀어쓴 문장에는 앞선 고객 발언의 다른 요금제가 섞여 있다
    private static final String RESOLVED = "[대상을 확인할 이전 고객 발언] 5G 라이트 알려줘 [현재 후속 질문] 그 요금제로 가입할래";

    @Test
    void 원문에_이름이_있으면_원문을_따른다() {
        assertThat(named("LTE 맥스로 바꿔줘", RESOLVED, RECOMMENDED)).containsExactly(MAX);
    }

    @Test
    void 추천을_가리키는_말이면_풀어쓴_문장의_이전_요금제보다_직전_추천을_쓴다() {
        assertThat(named("그 요금제로 가입할래", RESOLVED, RECOMMENDED)).containsExactly(STANDARD);
        assertThat(named("그걸로 바꿔줘", RESOLVED, RECOMMENDED)).containsExactly(STANDARD);
        assertThat(named("방금 추천한 걸로 가입해줘", RESOLVED, RECOMMENDED)).containsExactly(STANDARD);
    }

    @Test
    void 가리키는_말이_있어도_직전_추천이_없으면_풀어쓴_문장에서_찾는다() {
        assertThat(named("그 요금제로 가입할래", RESOLVED, null)).containsExactly(LITE);
    }

    @Test
    void 다른_요금제를_고르겠다는_말은_대상을_정하지_않는다() {
        assertThat(named("다른 요금제로 바꿔줘", RESOLVED, RECOMMENDED)).isEmpty();
        assertThat(named("딴 걸로 가입하고 싶어", RESOLVED, RECOMMENDED)).isEmpty();
        assertThat(named("다른 요금제 말고 그걸로 할게", RESOLVED, RECOMMENDED)).containsExactly(STANDARD);
    }

    @Test
    void 가리키는_말이_없으면_기존대로_풀어쓴_문장_다음에_직전_추천을_쓴다() {
        assertThat(named("요금제 가입하고 싶어", RESOLVED, RECOMMENDED)).containsExactly(LITE);
        assertThat(named("요금제 가입하고 싶어", "요금제 가입하고 싶어", RECOMMENDED)).containsExactly(STANDARD);
        assertThat(named("요금제 가입하고 싶어", "요금제 가입하고 싶어", null)).isEmpty();
    }

    private static List<Plan> named(String original, String resolved, String recommended) {
        return PlanTaskSupport.namedPlans(SELLING, original, resolved, recommended);
    }

    private static Plan plan(long id, String name) {
        return Plan.builder().planId(id).name(name).monthlyFee(10_000).dataGb(new BigDecimal("10"))
                .targetGroup(Plan.TargetGroup.GENERAL).status(Plan.Status.ACTIVE).build();
    }
}
