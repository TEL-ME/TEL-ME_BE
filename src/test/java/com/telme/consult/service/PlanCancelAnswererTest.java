package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.dto.res.PlanResponse;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.ContractPenalty;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanCancelAnswererTest {

    private static final long USER_ID = 7L;
    private static final ContractPenalty PENALTY = new ContractPenalty(24, 3, 41_250, 100, 41_250);

    private SubscriptionQueryService queries;
    private PlanCancelAnswerer answerer;

    @BeforeEach
    void setUp() {
        queries = mock(SubscriptionQueryService.class);
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription().build()));
        when(queries.getTerminationPenalty(USER_ID)).thenReturn(PENALTY);
        answerer = new PlanCancelAnswerer(queries);
    }

    @Test
    @DisplayName("해지하지 않고 지금 요금제·약정·위약금·바로 끊기는 영향을 먼저 보여준 뒤 내 요금제 화면 링크를 준다")
    void 영향_안내() {
        var outcome = answerer.answer(USER_ID, "요금제 해지해줘", null);

        assertThat(outcome.content()).isEqualTo("LTE 맥스를 해지하면 바로 끊기고 되돌릴 수 없어요. 24개월 약정 중 3개월을 쓰셨어요."
                + " 지금 해지하면 위약금 41,250원이 청구돼요. 해지는 내 요금제 화면에서 진행할 수 있어요.");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.CANCEL);
    }

    @Test
    @DisplayName("일시 정지 중이거나 적용 전 변경이 있으면 함께 끝난다고 알려주고, 약정이 없으면 위약금이 없다고 한다")
    void 함께_사라지는_것() {
        when(queries.getTerminationPenalty(USER_ID)).thenReturn(ContractPenalty.none());
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription()
                .pause(new SubscriptionResponse.PauseResponse(Instant.now(), Instant.now(), 27_500))
                .pendingChange(new SubscriptionResponse.ScheduledPlanResponse(
                        PlanResponse.builder().planId(4L).name("5G 라이트").build(), Instant.now()))
                .build()));

        assertThat(answerer.answer(USER_ID, "해지해줘", null).content())
                .contains("위약금은 없어요", "일시 정지도 함께 끝나요", "요금제 변경 신청도 함께 사라져요")
                .doesNotContain("약정 중");
    }

    @Test
    @DisplayName("개통 전이면 신청이 취소된다고 알려준다")
    void 개통_전() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription().currentPlan(null)
                .pendingActivation(new SubscriptionResponse.ScheduledPlanResponse(
                        PlanResponse.builder().planId(4L).name("5G 라이트").build(), Instant.now()))
                .build()));

        assertThat(answerer.answer(USER_ID, "해지해줘", null).content())
                .startsWith("개통 예정인 5G 라이트를 해지하면 신청이 바로 취소되고 되돌릴 수 없어요.");
    }

    @Test
    @DisplayName("비회원이거나 가입한 요금제가 없으면 화면 링크 없이 알려준다")
    void 해지할_수_없는_상태() {
        assertThat(answerer.answer(null, "해지해줘", null).content()).contains("로그인");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.notSubscribed());
        var outcome = answerer.answer(USER_ID, "해지해줘", null);
        assertThat(outcome.content()).contains("해지할 요금제가 없어요");
        assertThat(outcome.followUps()).doesNotContain(PlanTaskLinks.CANCEL);
    }

    private static SubscriptionResponse.SubscriptionResponseBuilder subscription() {
        return SubscriptionResponse.builder()
                .subscriptionId(1L)
                .subscribedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .currentPlan(PlanResponse.builder().planId(5L).name("LTE 맥스").monthlyFee(55_000).build());
    }
}
