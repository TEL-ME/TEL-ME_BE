package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.SubscriptionQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanSubscribeAnswererTest {

    private static final long USER_ID = 7L;
    private static final Plan LITE = plan(4L, "5G 라이트", 45_000, "12", Plan.TargetGroup.GENERAL);
    private static final Plan MAX = plan(5L, "LTE 맥스", 55_000, "30", Plan.TargetGroup.GENERAL);
    private static final Plan SENIOR = plan(13L, "5G 시니어", 39_000, "10", Plan.TargetGroup.SENIOR);

    private SubscriptionQueryService queries;
    private PlanSubscribeAnswerer answerer;

    @BeforeEach
    void setUp() {
        PlanRepository plans = mock(PlanRepository.class);
        queries = mock(SubscriptionQueryService.class);
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(LITE, MAX, SENIOR));
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.notSubscribed());
        answerer = new PlanSubscribeAnswerer(plans, queries, new SubscriptionProperties(false));
    }

    @Test
    @DisplayName("비회원이면 로그인 안내만 한다")
    void 비회원() {
        assertThat(answerer.answer(null, "5G 라이트 신청해줘", null).content()).contains("로그인");
    }

    @Test
    @DisplayName("요금제 신청해줘처럼 이름이 없으면 가입 화면에서 고르도록 링크와 요금제 종류 버튼을 준다")
    void 이름_없음() {
        var outcome = answerer.answer(USER_ID, "요금제 신청해줘", null);

        assertThat(outcome.content()).startsWith("요금제 가입 화면에서 원하는 요금제를 골라 신청해 주세요.");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.SUBSCRIBE, "요금제 종류 알려줘");
    }

    @Test
    @DisplayName("신청할 요금제를 말하면 요금·약정·개통 시점을 알려주고 가입 화면 링크를 준다")
    void 신청_안내() {
        var outcome = answerer.answer(USER_ID, "5G 라이트 신청해줘", null);

        assertThat(outcome.content()).isEqualTo("5G 라이트(월 45,000원, 데이터 12GB)는 요금제 가입 화면에서 신청할 수 있어요."
                + " 약정 없이 가입하거나 24개월 약정으로 월정액 25% 할인을 받을 수 있고, 신청하면 다음 날 오전 9시에 개통돼요.");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.SUBSCRIBE);
        assertThat(answerer.answer(USER_ID, "5G 시니어 가입할게요", null).content()).endsWith("화면에서 생년월일을 입력해요.");
    }

    @Test
    @DisplayName("이미 가입했으면 신청이 아니라 변경 화면 링크를 주고, 같은 요금제면 이미 가입했다고 한다")
    void 이미_가입() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(SubscriptionResponse.builder().subscriptionId(1L)
                .subscribedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .currentPlan(PlanResponse.builder().planId(5L).name("LTE 맥스").build()).build()));

        var other = answerer.answer(USER_ID, "5G 라이트 신청해줘", null);
        assertThat(other.content()).contains("이미 가입한 요금제가 있어서 새로 신청할 수 없어요");
        assertThat(other.followUps()).containsExactly(PlanTaskLinks.CHANGE);
        assertThat(answerer.answer(USER_ID, "LTE 맥스 가입할게요", null).content()).isEqualTo("LTE 맥스는 이미 가입하신 요금제예요.");
    }

    private static Plan plan(long id, String name, int fee, String dataGb, Plan.TargetGroup group) {
        return Plan.builder().planId(id).name(name).monthlyFee(fee).dataGb(new BigDecimal(dataGb))
                .targetGroup(group).status(Plan.Status.ACTIVE).build();
    }
}
