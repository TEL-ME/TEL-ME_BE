package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanChangeAnswererTest {

    private static final long USER_ID = 7L;
    private static final Plan LITE = plan(4L, "5G 라이트", 45_000, "12", Plan.TargetGroup.GENERAL);
    private static final Plan MAX = plan(5L, "LTE 맥스", 55_000, "30", Plan.TargetGroup.GENERAL);
    private static final Plan SENIOR = plan(13L, "5G 시니어", 39_000, "10", Plan.TargetGroup.SENIOR);
    private static final Plan PREMIUM = plan(6L, "5G 프리미엄", 75_000, "150", Plan.TargetGroup.GENERAL);

    // 한국 시각 10월 17일 12시. 기본 가입일(8월 1일)과 달이 달라 가입 당월 제한에 걸리지 않는다
    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    private PlanRepository plans;
    private SubscriptionQueryService queries;
    private SubscriptionHistoryRepository histories;
    private PlanChangeAnswerer answerer;

    @BeforeEach
    void setUp() {
        plans = mock(PlanRepository.class);
        histories = mock(SubscriptionHistoryRepository.class);
        queries = mock(SubscriptionQueryService.class);
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(LITE, MAX, SENIOR, PREMIUM));
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).build()));
        answerer = answerer(false);
    }

    @Test
    @DisplayName("비회원이면 로그인 안내만 한다")
    void 비회원() {
        assertThat(answerer.answer(null, "5G 라이트로 바꿔줘", null).content()).contains("로그인");
    }

    @Test
    @DisplayName("바꿀 요금제를 말하면 요금·적용 시점을 알려주고 요금제 변경 화면 링크를 준다")
    void 변경_안내() {
        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null);

        assertThat(outcome.content()).isEqualTo("지금 쓰는 LTE 맥스에서 5G 라이트(월 45,000원, 데이터 12GB)로 바꾸려면"
                + " 요금제 변경 화면에서 5G 라이트를 골라 주세요. 변경은 신청한 다음 날 0시부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요.");
        assertThat(outcome.followUps()).containsExactly("5G 라이트 변경하러 가기");
    }

    @Test
    @DisplayName("지금 요금제와 바꿀 요금제를 함께 말하면 바꿀 요금제로 안내하고, 원문에 이름이 없으면 풀어쓴 문장에서 찾는다")
    void 요금제_찾기() {
        assertThat(answerer.answer(USER_ID, "LTE 맥스에서 5G 라이트로 바꿔줘", null).content()).contains("5G 라이트(월 45,000원");
        assertThat(answerer.answer(USER_ID, "그걸로 바꿔줘", "5G 라이트로 바꿔줘").content()).contains("5G 라이트(월 45,000원");
        assertThat(answerer.answer(USER_ID, "5G 라이트로 바꿔줘", "5G 시니어 말고 5G 라이트로 바꿔줘").content())
                .contains("5G 라이트(월 45,000원");
    }

    @Test
    @DisplayName("요금제 이름이 없거나 여럿이면 변경 화면에서 고르도록 링크와 요금제 종류 버튼을 준다")
    void 이름_없음() {
        var outcome = answerer.answer(USER_ID, "요금제 바꿔줘", null);

        assertThat(outcome.content()).startsWith("요금제 변경 화면에서 바꿀 요금제를 골라 주세요.");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.CHANGE, "요금제 종류 알려줘");
        assertThat(answerer.answer(USER_ID, "5G 라이트랑 5G 시니어로 바꿔줘", null).followUps()).contains(PlanTaskLinks.CHANGE);
    }

    @Test
    @DisplayName("나이 확인이 필요한 요금제는 화면에서 생년월일을 입력한다고 덧붙인다")
    void 나이_확인() {
        assertThat(answerer.answer(USER_ID, "5G 시니어로 바꿔줘", null).content()).endsWith("화면에서 생년월일을 입력해요.");
    }

    @Test
    @DisplayName("가입한 요금제가 없으면 변경이 아니라 가입 화면 링크를 준다")
    void 미가입() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.notSubscribed());

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null);

        assertThat(outcome.content()).contains("아직 가입한 요금제가 없어요");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.SUBSCRIBE);
    }

    @Test
    @DisplayName("같은 요금제·일시 정지 중·이미 변경 신청·개통 전이면 화면 링크 없이 이유를 알려준다")
    void 바꿀_수_없는_상태() {
        assertThat(answerer.answer(USER_ID, "LTE 맥스로 바꿔줘", null).content()).isEqualTo("LTE 맥스는 지금 쓰고 계신 요금제예요.");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).pause(
                new SubscriptionResponse.PauseResponse(Instant.now(), Instant.now(), 27_500)).build()));
        assertThat(answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null).content()).contains("일시 정지 중에는");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).pendingChange(
                new SubscriptionResponse.ScheduledPlanResponse(planResponse(SENIOR), Instant.parse("2026-10-17T15:00:00Z")))
                .build()));
        assertThat(answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null).content())
                .isEqualTo("이미 5G 시니어로 변경을 신청해 두셨어요. 10월 18일 0시부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요.");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).currentPlan(null).build()));
        var beforeActivation = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null);
        assertThat(beforeActivation.content()).contains("개통 전에는");
        assertThat(beforeActivation.followUps()).doesNotContain(PlanTaskLinks.CHANGE);
    }

    @Test
    @DisplayName("받침으로 끝나는 요금제 이름에는 은을 붙인다")
    void 받침_조사() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(PREMIUM).build()));

        assertThat(answerer.answer(USER_ID, "5G 프리미엄으로 바꿔줘", null).content()).isEqualTo("5G 프리미엄은 지금 쓰고 계신 요금제예요.");
    }

    @Test
    @DisplayName("가입한 달이면 변경 화면 링크 없이 다음 달부터 바꿀 수 있다고 알려준다")
    void 가입_당월() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX)
                .subscribedAt(Instant.parse("2026-10-05T00:00:00Z")).build()));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null);

        assertThat(outcome.content()).isEqualTo("가입한 달에는 요금제를 바꿀 수 없어요. 다음 달 1일부터 바꿀 수 있어요.");
        assertThat(outcome.followUps()).doesNotContain(PlanTaskLinks.CHANGE);
    }

    @Test
    @DisplayName("이번 달에 이미 바꿔서 적용까지 끝났으면 변경 대기가 없어도 이력으로 월 1회 제한을 알려준다")
    void 월_1회_제한() {
        when(histories.existsChangeRequestedBetween(1L, Instant.parse("2026-09-30T15:00:00Z"),
                Instant.parse("2026-10-31T15:00:00Z"))).thenReturn(true);

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null);

        assertThat(outcome.content()).startsWith("요금제는 한 달에 한 번만 바꿀 수 있어요. 이번 달에는 이미 바꾸셨어서");
        assertThat(outcome.followUps()).doesNotContain(PlanTaskLinks.CHANGE);
    }

    @Test
    @DisplayName("시연 모드에서는 기존 변경 규칙처럼 가입 당월·월 1회 제한을 생략하고 변경 화면으로 안내한다")
    void 시연_모드() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX)
                .subscribedAt(Instant.parse("2026-10-05T00:00:00Z")).build()));
        when(histories.existsChangeRequestedBetween(any(), any(), any())).thenReturn(true);

        var outcome = answerer(true).answer(USER_ID, "5G 라이트로 바꿔줘", null);

        assertThat(outcome.content()).contains("요금제 변경 화면에서 5G 라이트를 골라 주세요", "변경은 바로 적용되고");
        assertThat(outcome.followUps()).containsExactly("5G 라이트 변경하러 가기");
    }

    private PlanChangeAnswerer answerer(boolean demoMode) {
        return new PlanChangeAnswerer(plans, queries, new SubscriptionProperties(demoMode), histories,
                new SubscriptionCalendar(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static SubscriptionResponse.SubscriptionResponseBuilder subscription(Plan current) {
        return SubscriptionResponse.builder()
                .subscriptionId(1L)
                .subscribedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .currentPlan(planResponse(current));
    }

    private static PlanResponse planResponse(Plan plan) {
        return PlanResponse.builder().planId(plan.getPlanId()).name(plan.getName()).monthlyFee(plan.getMonthlyFee()).build();
    }

    private static Plan plan(long id, String name, int fee, String dataGb, Plan.TargetGroup group) {
        return Plan.builder().planId(id).name(name).monthlyFee(fee).dataGb(new BigDecimal(dataGb))
                .targetGroup(group).status(Plan.Status.ACTIVE).build();
    }
}
