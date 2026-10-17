package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.consult.dto.MissingCondition;
import com.telme.global.common.exception.GeneralException;
import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.config.SubscriptionProperties;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.subscription.service.SubscriptionSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanChangeAnswererTest {

    private static final long USER_ID = 7L;
    private static final Plan LITE = plan(4L, "5G 라이트", 45_000, "12", Plan.TargetGroup.GENERAL);
    private static final Plan MAX = plan(5L, "LTE 맥스", 55_000, "30", Plan.TargetGroup.GENERAL);
    private static final Plan SENIOR = plan(13L, "5G 시니어", 39_000, "10", Plan.TargetGroup.SENIOR);
    private static final Plan PREMIUM = plan(6L, "5G 프리미엄", 75_000, "150", Plan.TargetGroup.GENERAL);

    private PlanRepository plans;
    private SubscriptionQueryService queries;
    private SubscriptionCommandService commands;
    private PlanChangeAnswerer answerer;

    @BeforeEach
    void setUp() {
        plans = mock(PlanRepository.class);
        queries = mock(SubscriptionQueryService.class);
        commands = mock(SubscriptionCommandService.class);
        when(plans.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE)).thenReturn(List.of(LITE, MAX, SENIOR, PREMIUM));
        when(queries.getMine(USER_ID)).thenReturn(subscribedTo(MAX));
        answerer = new PlanChangeAnswerer(plans, queries, commands, new SubscriptionProperties(false));
    }

    @Test
    @DisplayName("비회원이면 로그인 안내만 하고 아무것도 바꾸지 않는다")
    void 비회원() {
        var outcome = answerer.answer(null, "5G 라이트로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.content()).contains("로그인");
        verify(commands, never()).changePlan(any(), any(), any());
    }

    @Test
    @DisplayName("요금제 이름이 없으면 이름을 알려 달라고 하고 요금제 종류 버튼을 준다")
    void 이름_없음() {
        var outcome = answerer.answer(USER_ID, "그걸로 바꿔줘", "그걸로 바꿔줘", Map.of(), true);

        assertThat(outcome.content()).contains("요금제 이름과 함께");
        assertThat(outcome.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    @DisplayName("문맥으로 풀어쓴 문장에 요금제 이름이 있으면 그 요금제로 확인을 받는다")
    void 풀어쓴_문장에서_찾음() {
        var outcome = answerer.answer(USER_ID, "그걸로 바꿔줘", "5G 라이트로 바꿔줘", Map.of(), true);

        assertThat(outcome.asks()).extracting(MissingCondition::question).singleElement()
                .asString().contains("5G 라이트");
    }

    @Test
    @DisplayName("두 요금제를 함께 말하면 하나만 말해 달라고 한다")
    void 두_요금제() {
        var outcome = answerer.answer(USER_ID, "5G 라이트랑 5G 시니어로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.content()).contains("한 요금제로만");
    }

    @Test
    @DisplayName("지금 요금제와 바꿀 요금제를 함께 말하면 바꿀 요금제로 확인을 받는다")
    void 지금_요금제와_함께_말함() {
        var outcome = answerer.answer(USER_ID, "LTE 맥스에서 5G 라이트로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.asks()).extracting(MissingCondition::question).singleElement()
                .asString().contains("지금 쓰는 LTE 맥스에서 5G 라이트(");
    }

    @Test
    @DisplayName("원문에 요금제 이름이 있으면 문맥으로 풀어쓴 문장에 섞인 다른 요금제는 보지 않는다")
    void 원문_우선() {
        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", "5G 시니어 말고 5G 라이트로 바꿔줘", Map.of(), true);

        assertThat(outcome.asks()).extracting(MissingCondition::question).singleElement()
                .asString().contains("5G 라이트(");
    }

    @Test
    @DisplayName("받침으로 끝나는 요금제 이름에는 은을 붙인다")
    void 받침_조사() {
        when(queries.getMine(USER_ID)).thenReturn(subscribedTo(PREMIUM));

        var outcome = answerer.answer(USER_ID, "5G 프리미엄으로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.content()).isEqualTo("5G 프리미엄은 지금 쓰고 계신 요금제예요.");
    }

    @Test
    @DisplayName("가입한 요금제가 없으면 요금제 화면으로 안내하고 새로 가입시키지 않는다")
    void 미가입() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.notSubscribed());

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.content()).contains("요금제 화면에서 신청");
        verify(commands, never()).changePlan(any(), any(), any());
    }

    @Test
    @DisplayName("지금 쓰는 요금제로 바꿔 달라고 하면 이미 쓰고 있다고 알려준다")
    void 같은_요금제() {
        var outcome = answerer.answer(USER_ID, "LTE 맥스로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.content()).contains("LTE 맥스는 지금 쓰고 계신 요금제예요");
    }

    @Test
    @DisplayName("일시 정지 중이면 확인을 묻지 않고 바로 바꿀 수 없다고 알려준다")
    void 일시_정지_중() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).pause(
                new SubscriptionResponse.PauseResponse(Instant.now(), Instant.now(), 3_850)).build()));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("일시 정지 중에는");
    }

    @Test
    @DisplayName("이미 변경 신청이 있으면 확인을 묻지 않고 신청 내용을 알려준다")
    void 이미_변경_신청() {
        when(queries.getMine(USER_ID)).thenReturn(SubscriptionMeResponse.of(subscription(MAX).pendingChange(
                new SubscriptionResponse.ScheduledPlanResponse(planResponse(SENIOR), Instant.parse("2026-10-17T15:00:00Z")))
                .build()));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null, Map.of(), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).isEqualTo(
                "이미 5G 시니어로 변경을 신청해 두셨어요. 10월 18일 0시부터 적용되고, 요금제는 한 달에 한 번만 바꿀 수 있어요.");
    }

    @Test
    @DisplayName("변경 중 예상하지 못한 오류가 나도 FAQ 답으로 넘기지 않고 바꾸지 못했다고 알려준다")
    void 예상하지_못한_오류() {
        when(commands.changePlan(anyLong(), anyLong(), any())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null,
                Map.of(PlanChangeAnswerer.CONFIRM_KEY, "예"), true);

        assertThat(outcome.content()).contains("지금은 요금제를 바꾸지 못했어요");
    }

    @Test
    @DisplayName("처음 요청하면 바꾸지 않고 지금 요금제·바꿀 요금제·적용 시점을 담아 예/아니요로 확인한다")
    void 확인_질문() {
        var outcome = answerer.answer(USER_ID, "오지 라이트로 바꿔 주세요", null, Map.of(), true);

        MissingCondition ask = outcome.asks().getFirst();
        assertThat(ask.key()).isEqualTo(PlanChangeAnswerer.CONFIRM_KEY);
        assertThat(ask.options()).containsExactly("예", "아니요");
        assertThat(ask.question()).contains("지금 쓰는 LTE 맥스에서 5G 라이트(월 45,000원, 데이터 12GB)로 바꿀까요?",
                "다음 날 0시부터", "한 달에 한 번만");
        verify(commands, never()).changePlan(any(), any(), any());
    }

    @Test
    @DisplayName("확인 질문을 할 수 없는 흐름이면 바꾸지 않는다")
    void 확인_불가() {
        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null, Map.of(), false);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("확인을 받은 뒤에");
        verify(commands, never()).changePlan(any(), any(), any());
    }

    @Test
    @DisplayName("아니요라고 하면 바꾸지 않는다")
    void 거절() {
        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null,
                Map.of(PlanChangeAnswerer.CONFIRM_KEY, "아니요"), true);

        assertThat(outcome.content()).contains("바꾸지 않았어요");
        verify(commands, never()).changePlan(any(), any(), any());
    }

    @Test
    @DisplayName("예라고 하면 기존 요금제 변경을 그대로 부르고 적용 시각을 알려준다")
    void 변경() {
        when(commands.changePlan(USER_ID, LITE.getPlanId(), null)).thenReturn(new SubscriptionSnapshot(null, MAX, null,
                new SubscriptionSnapshot.ScheduledPlan(LITE, Instant.parse("2026-10-17T15:00:00Z")), null, null));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null,
                Map.of(PlanChangeAnswerer.CONFIRM_KEY, "예"), true);

        verify(commands).changePlan(USER_ID, LITE.getPlanId(), null);
        assertThat(outcome.content()).isEqualTo("5G 라이트로 변경을 신청했어요. 10월 18일 0시부터 적용돼요.");
        assertThat(outcome.followUps()).containsExactly("내 요금제 알려줘");
    }

    @Test
    @DisplayName("이번 달에 이미 바꿨으면 기존 규칙의 거절을 대화체로 알려준다")
    void 월_1회_제한() {
        when(commands.changePlan(anyLong(), anyLong(), any()))
                .thenThrow(new GeneralException(SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED));

        var outcome = answerer.answer(USER_ID, "5G 라이트로 바꿔줘", null,
                Map.of(PlanChangeAnswerer.CONFIRM_KEY, "예"), true);

        assertThat(outcome.content()).contains("한 달에 한 번만", "다음 달에");
    }

    @Test
    @DisplayName("생년월일이 필요한 요금제는 요금제 화면에서 바꾸도록 안내한다")
    void 생년월일_필요() {
        when(commands.changePlan(anyLong(), anyLong(), any()))
                .thenThrow(new GeneralException(SubscriptionErrorCode.BIRTH_DATE_REQUIRED));

        var outcome = answerer.answer(USER_ID, "5G 시니어로 바꿔줘", null,
                Map.of(PlanChangeAnswerer.CONFIRM_KEY, "예"), true);

        assertThat(outcome.content()).contains("5G 시니어는 나이 확인이 필요한 요금제예요", "요금제 화면에서 생년월일");
    }

    @Test
    @DisplayName("추천 답변에 지금 요금제 말고 다른 요금제가 하나만 나오면 그 요금제로 바꾸는 버튼을 준다")
    void 변경_버튼() {
        assertThat(answerer.changeButton(USER_ID, "LTE 맥스(30GB)에서 5G 라이트(12GB)로 바꾸면 월 10,000원 아낄 수 있어요."))
                .contains("5G 라이트로 바꿔줘");
    }

    @Test
    @DisplayName("다른 요금제가 없거나 여럿이거나 회원이 아니면 변경 버튼을 주지 않는다")
    void 변경_버튼_없음() {
        assertThat(answerer.changeButton(USER_ID, "지금 쓰는 LTE 맥스가 가장 잘 맞아요.")).isEmpty();
        assertThat(answerer.changeButton(USER_ID, "5G 라이트나 5G 시니어를 추천해요.")).isEmpty();
        assertThat(answerer.changeButton(null, "5G 라이트를 추천해요.")).isEmpty();
    }

    private static SubscriptionMeResponse subscribedTo(Plan current) {
        return SubscriptionMeResponse.of(subscription(current).build());
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
