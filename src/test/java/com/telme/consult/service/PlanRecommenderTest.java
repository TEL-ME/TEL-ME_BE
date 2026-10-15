package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.service.PlanRecommender.Demand;
import com.telme.consult.service.PlanRecommender.Kind;
import com.telme.consult.service.PlanRecommender.Recommendation;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.usage.service.UsageGenerator;
import com.telme.usage.service.UsageLimits;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlanRecommenderTest {

    static final long GB = 1024;

    @Autowired PlanRepository planRepository;

    PlanRecommender recommender = new PlanRecommender();

    @Test
    void 사용량에_여유를_둔_한도를_채우는_가장_싼_요금제를_권한다() {
        // 35GB x 1.2 = 42GB가 필요하다. 5G 스탠다드(50GB, 65,000원)가 LTE 맥스(100GB, 69,000원)보다 싸다
        Recommendation recommendation = recommend("FIVE_G_PREMIUM", demand(35 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(recommendation.kind()).isEqualTo(Kind.CHEAPER);
    }

    @Test
    void 지금_요금제가_알맞고_더_싼_요금제도_없으면_그대로_쓴다() {
        Recommendation recommendation = recommend("FIVE_G_STANDARD", demand(35 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(recommendation.kind()).isEqualTo(Kind.KEEP);
    }

    @Test
    void 한도에_여유가_없으면_더_큰_요금제를_권한다() {
        // 60GB x 1.2 = 72GB가 필요하다. 5G 스탠다드(50GB)는 모자라고 LTE 맥스(100GB)가 가장 싸게 채운다
        Recommendation recommendation = recommend("FIVE_G_STANDARD", demand(60 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("LTE_MAX");
        assertThat(recommendation.kind()).isEqualTo(Kind.UPGRADE);
    }

    @Test
    void 여유_20퍼센트가_정확히_맞는_한도는_채운다고_본다() {
        // 40GB x 1.2 = 48GB는 5G 스탠다드(50GB)에 들어가고, 50GB x 1.2 = 60GB는 들어가지 않는다
        assertThat(PlanRecommender.fits(plan("FIVE_G_STANDARD"), demand(40 * GB))).isTrue();
        assertThat(PlanRecommender.fits(plan("FIVE_G_STANDARD"), demand(50 * GB))).isFalse();
        // 알뜰 미니 1.5GB는 1.25GB x 1.2 = 1.5GB까지
        assertThat(PlanRecommender.fits(plan("BUDGET_MINI"), demand(1280))).isTrue();
        assertThat(PlanRecommender.fits(plan("BUDGET_MINI"), demand(1281))).isFalse();
    }

    @Test
    void 어느_요금제도_채우지_못하면_데이터가_가장_큰_무제한_요금제를_권한다() {
        Recommendation recommendation = recommend("FIVE_G_PREMIUM", demand(200 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_UNLIMITED");
        assertThat(recommendation.kind()).isEqualTo(Kind.UPGRADE);
    }

    @Test
    void 이미_가장_큰_요금제를_쓰고_있으면_그대로_쓴다() {
        assertThat(recommend("FIVE_G_UNLIMITED", demand(500 * GB), true).kind()).isEqualTo(Kind.KEEP);
    }

    @Test
    void 최근에_요금제를_바꿨으면_더_싼_요금제는_권하지_않는다() {
        Recommendation recommendation = recommend("FIVE_G_PREMIUM", demand(35 * GB), false);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_PREMIUM");
        assertThat(recommendation.kind()).isEqualTo(Kind.KEEP);
    }

    @Test
    void 지금_요금제가_모자라면_더_싼_요금제_제한과_상관없이_맞는_요금제를_권한다() {
        Recommendation recommendation = recommend("FIVE_G_LITE", demand(20 * GB), false);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(recommendation.kind()).isEqualTo(Kind.UPGRADE);
    }

    @Test
    void 통화나_문자_한도가_모자라도_채우는_요금제를_권한다() {
        // 데이터 1GB는 알뜰 미니가 채우지만 통화 600분 x 1.2 = 720분은 LTE 베이직(700분)도 모자라 통화 무제한 요금제 중 가장 싼 5G 라이트가 필요하다
        Recommendation voice = recommend("BUDGET_MINI", new Demand(1 * GB, 600, 10), true);
        assertThat(voice.plan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(voice.kind()).isEqualTo(Kind.UPGRADE);

        // 통화 500분 x 1.2 = 600분은 LTE 베이직(700분, 33,000원)이 가장 싸게 채운다
        assertThat(recommend("BUDGET_MINI", new Demand(1 * GB, 500, 10), true).plan().getCode()).isEqualTo("LTE_BASIC");
        // 문자 120건 x 1.2 = 144건은 알뜰 세이브(300건, 25,000원)가 가장 싸게 채운다
        assertThat(recommend("BUDGET_MINI", new Demand(1 * GB, 10, 120), true).plan().getCode()).isEqualTo("BUDGET_SAVE");
    }

    @Test
    void 청소년과_시니어_요금제는_후보에_넣었을_때만_권한다() {
        Plan current = plan("FIVE_G_STANDARD");
        Demand demand = demand(20 * GB);

        assertThat(recommender.recommend(candidates(null), current, demand, true).kind()).isEqualTo(Kind.KEEP);
        Recommendation youth = recommender.recommend(candidates(Plan.TargetGroup.YOUTH), current, demand, true);
        assertThat(youth.plan().getCode()).isEqualTo("FIVE_G_YOUTH");
        assertThat(youth.kind()).isEqualTo(Kind.CHEAPER);
    }

    @Test
    void 가장_싼_요금제가_여럿이면_데이터가_큰_요금제를_고른다() {
        List<Plan> sameFee = List.of(plan("LTE_PLUS"), plan("FIVE_G_SENIOR"));

        // 월 49,000원으로 같고 데이터는 5G 시니어 20GB, LTE 플러스 15GB
        Recommendation recommendation = recommender.recommend(sameFee, plan("LTE_MAX"), demand(5 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("FIVE_G_SENIOR");
    }

    @Test
    void 내릴_때는_한도에_더_여유가_있는_요금제만_권한다() {
        // 38GB: 5G 스탠다드(50GB)는 지금 요금제로 쓰기엔 충분(45.6GB 필요)하지만 내려가기엔 빠듯해(53.2GB 필요) LTE 맥스(100GB)를 권한다
        Recommendation recommendation = recommend("FIVE_G_PREMIUM", demand(38 * GB), true);

        assertThat(recommendation.plan().getCode()).isEqualTo("LTE_MAX");
        assertThat(recommendation.kind()).isEqualTo(Kind.CHEAPER);
        // 같은 38GB를 쓰는 5G 스탠다드 사용자는 올리지도 내리지도 않는다
        assertThat(recommend("FIVE_G_STANDARD", demand(38 * GB), true).kind()).isEqualTo(Kind.KEEP);
    }

    // 추천을 따라 요금제를 바꾸면 사용량이 새 한도에 맞춰 줄어드는 사용량 모델 때문에 계속 따라가면 아래로 내려간다.
    // 석 달 안에는 다시 내리지 않고 내릴 때 여유를 더 두면 한 해에 바뀌는 횟수가 제한되고 한도 경계에서 오가지 않는다
    @Test
    void 추천을_계속_따라가도_석_달에_한_번보다_자주_바뀌지_않는다() {
        UsageGenerator generator = new UsageGenerator();
        List<Plan> general = candidates(null);
        for (long userId = 1; userId <= 500; userId++) {
            Plan current = plan("FIVE_G_PREMIUM");
            UsageLimits habit = UsageLimits.of(current);
            List<long[]> recent = new java.util.ArrayList<>();
            int changes = 0;
            int lastChange = -100;
            for (int month = 0; month < 12; month++) {
                var usage = generator.generate(userId, LocalDate.of(2026, 1, 1).plusMonths(month), habit, UsageLimits.of(current));
                recent.add(new long[] {usage.dataMb(), usage.voiceMinutes(), usage.smsCount()});
                List<long[]> window = recent.subList(Math.max(0, recent.size() - 3), recent.size());
                Demand demand = new Demand(average(window, 0), average(window, 1), average(window, 2));
                Recommendation recommendation = recommender.recommend(general, current, demand, month - lastChange >= 3);
                if (!recommendation.plan().equals(current)) {
                    current = recommendation.plan();
                    changes++;
                    lastChange = month;
                }
            }
            assertThat(changes).as("사용자 %d", userId).isLessThanOrEqualTo(4);
        }
    }

    private static long average(List<long[]> window, int index) {
        return Math.round(window.stream().mapToLong(values -> values[index]).average().orElse(0));
    }

    private Recommendation recommend(String currentCode, Demand demand, boolean allowDowngrade) {
        return recommender.recommend(candidates(null), plan(currentCode), demand, allowDowngrade);
    }

    private List<Plan> candidates(Plan.TargetGroup extra) {
        return planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE).stream()
                .filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.GENERAL || plan.getTargetGroup() == extra)
                .toList();
    }

    private Plan plan(String code) {
        return planRepository.findAll().stream().filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow();
    }

    private static Demand demand(long dataMb) {
        return new Demand(dataMb, 50, 20);
    }
}
