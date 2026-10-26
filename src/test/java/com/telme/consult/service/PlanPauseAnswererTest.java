package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.plan.dto.res.PlanResponse;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanPauseAnswererTest {

    private static final long USER_ID = 7L;
    // 한국 시각 10월 17일 12시
    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    private SubscriptionQueryService queries;
    private PlanPauseAnswerer answerer;

    @BeforeEach
    void setUp() {
        queries = mock(SubscriptionQueryService.class);
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription().build()));
        answerer = new PlanPauseAnswerer(queries, new SubscriptionCalendar(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("기간 한도와 요금 영향을 알려주고 내 요금제 화면 링크를 준다")
    void 정지_안내() {
        var outcome = answerer.answer(USER_ID, "일시정지해줘", null);

        assertThat(outcome.content()).isEqualTo("일시 정지는 내 요금제 화면에서 신청할 수 있어요. 한 번에 1일부터 30일까지,"
                + " 1년에 최대 90일까지 정지할 수 있고, 정지 기간에는 기본료의 50%만 내요. 기간이 끝나면 자동으로 다시 쓸 수 있어요.");
        assertThat(outcome.followUps()).containsExactly(PlanTaskLinks.PAUSE);
    }

    @Test
    @DisplayName("기간을 말하면 오늘 정지할 때 끝나는 날을, 한도를 넘으면 그 점을 먼저 알려준다")
    void 기간() {
        assertThat(answerer.answer(USER_ID, "7일 일시 정지해줘", null).content()).contains("오늘 7일 정지하면 10월 24일까지예요.");
        assertThat(answerer.answer(USER_ID, "두 달 일시정지해줘", null).content()).contains("60일은 한 번에 정지할 수 있는 기간을 넘어요.");
        assertThat(answerer.answer(USER_ID, "3일부터 7일까지 일시정지해줘", null).content())
                .doesNotContain("오늘 3일").startsWith("일시 정지는 내 요금제 화면에서 신청할 수 있어요. 한 번에");
    }

    @Test
    @DisplayName("비회원·미가입·개통 전·이미 정지 중이면 화면 링크 없이 상태를 알려준다")
    void 정지할_수_없는_상태() {
        assertThat(answerer.answer(null, "일시정지해줘", null).content()).contains("로그인");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription().pause(
                new SubscriptionResponse.PauseResponse(NOW, Instant.parse("2026-10-24T03:00:00Z"), 27_500)).build()));
        var paused = answerer.answer(USER_ID, "일시정지해줘", null);
        assertThat(paused.content()).startsWith("이미 일시 정지 중이에요. 10월 24일 12시에 자동으로 다시 쓸 수 있고");
        assertThat(paused.followUps()).doesNotContain(PlanTaskLinks.PAUSE);

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription().currentPlan(null).build()));
        assertThat(answerer.answer(USER_ID, "일시정지해줘", null).content()).contains("개통 전에는");

        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.notSubscribed());
        assertThat(answerer.answer(USER_ID, "일시정지해줘", null).content()).contains("가입한 요금제가 없어서");
    }

    private static SubscriptionResponse.SubscriptionResponseBuilder subscription() {
        return SubscriptionResponse.builder()
                .subscriptionId(1L)
                .subscribedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .currentPlan(PlanResponse.builder().planId(5L).name("LTE 맥스").monthlyFee(55_000).build());
    }
}
