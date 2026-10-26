package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.entity.Plan;
import com.telme.plan.exception.PlanErrorCode;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = "telme.subscription.demo-mode=false")
@Transactional
class SubscriptionCommandServiceTest {

    // 2026-10-12(월) 정오(KST)
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");
    static final Instant OCT_13_0900_KST = Instant.parse("2026-10-13T00:00:00Z");

    @MockitoBean Clock clock;

    @Autowired SubscriptionCommandService service;
    @Autowired SubscriptionStateReader stateReader;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionHistoryRepository historyRepository;
    @Autowired JdbcTemplate jdbc;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-service@test.com").name("구독").build()).getUserId();
        at(OCT_12);
    }

    @Test
    void 일반_요금제는_생년월일_없이_가입하고_다음_날_09시에_개통된다() {
        SubscriptionSnapshot snapshot = service.subscribe(userId, planId("FIVE_G_LITE"), null);

        assertThat(snapshot.currentPlan()).isNull();
        assertThat(snapshot.pendingActivation().plan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(snapshot.pendingActivation().effectiveFrom()).isEqualTo(OCT_13_0900_KST);
        assertThat(snapshot.pendingChange()).isNull();
        assertThat(snapshot.subscription().getStatus()).isEqualTo(Subscription.Status.ACTIVE);
        assertThat(historyOf(snapshot.subscription()))
                .extracting(SubscriptionHistory::getType).containsExactly(SubscriptionHistory.Type.SUBSCRIBE);
        assertThat(birthDateOf(userId)).isNull();
    }

    @Test
    void 일반_요금제에_보낸_생년월일은_저장하지_않는다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), LocalDate.of(1990, 1, 1));

        assertThat(birthDateOf(userId)).isNull();
    }

    @Test
    void 이미_가입한_사용자는_다시_가입할_수_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        assertError(() -> service.subscribe(userId, planId("LTE_BASIC"), null),
                SubscriptionErrorCode.ALREADY_SUBSCRIBED);
    }

    @Test
    void 없는_요금제와_판매_종료된_요금제는_가입할_수_없다() {
        assertError(() -> service.subscribe(userId, 999_999L, null), PlanErrorCode.PLAN_NOT_FOUND);

        jdbc.update("UPDATE plans SET status = 'DISCONTINUED' WHERE code = 'LTE_BASIC'");
        assertError(() -> service.subscribe(userId, planId("LTE_BASIC"), null), PlanErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    void 청소년_요금제는_생년월일이_없거나_미래이거나_너무_오래됐으면_거부한다() {
        Long plan = planId("FIVE_G_YOUTH");

        assertError(() -> service.subscribe(userId, plan, null), SubscriptionErrorCode.BIRTH_DATE_REQUIRED);
        assertError(() -> service.subscribe(userId, plan, LocalDate.of(2026, 10, 13)),
                SubscriptionErrorCode.INVALID_BIRTH_DATE);
        assertError(() -> service.subscribe(userId, plan, LocalDate.of(1900, 1, 1)),
                SubscriptionErrorCode.INVALID_BIRTH_DATE);
        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isEmpty();
    }

    @Test
    void 청소년_요금제는_내일_만_19세가_되는_사람까지_가입할_수_있다() {
        Long plan = planId("FIVE_G_YOUTH");

        // 2026-10-12 기준: 2007-10-13생은 내일 19세가 되므로 아직 만 18세
        service.subscribe(userId, plan, LocalDate.of(2007, 10, 13));
        assertThat(birthDateOf(userId)).isEqualTo(LocalDate.of(2007, 10, 13));
    }

    @Test
    void 청소년_요금제는_오늘_만_19세가_되면_가입할_수_없고_생년월일도_저장하지_않는다() {
        assertError(() -> service.subscribe(userId, planId("LTE_YOUTH"), LocalDate.of(2007, 10, 12)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        assertThat(birthDateOf(userId)).isNull();
    }

    @Test
    void 청소년_요금제는_오늘_만_14세가_되면_가입할_수_있고_하루_모자라면_거부한다() {
        Long plan = planId("LTE_YOUTH");

        // 2026-10-12 기준: 2012-10-13생은 내일 14세가 되므로 아직 만 13세
        assertError(() -> service.subscribe(userId, plan, LocalDate.of(2012, 10, 13)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        assertThat(birthDateOf(userId)).isNull();

        service.subscribe(userId, plan, LocalDate.of(2012, 10, 12));
        assertThat(birthDateOf(userId)).isEqualTo(LocalDate.of(2012, 10, 12));
    }

    @Test
    void 일반_요금제_가입자가_만_14세_미만으로_청소년_요금제로_변경하는_것도_거부한다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-10T03:00:00Z"));

        assertError(() -> service.changePlan(userId, planId("FIVE_G_YOUTH"), LocalDate.of(2015, 1, 1)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
    }

    @Test
    void 시니어_요금제는_오늘_만_65세가_되면_가입할_수_있고_하루_모자라면_거부한다() {
        Long plan = planId("LTE_SENIOR");

        assertError(() -> service.subscribe(userId, plan, LocalDate.of(1961, 10, 13)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        service.subscribe(userId, plan, LocalDate.of(1961, 10, 12));
        assertThat(birthDateOf(userId)).isEqualTo(LocalDate.of(1961, 10, 12));
    }

    @Test
    void 저장된_생년월일이_있으면_요청_값은_무시한다() {
        userRepository.findById(userId).orElseThrow().fillBirthDateIfMissing(LocalDate.of(1950, 3, 1));

        // 요청에 청소년 나이를 보내도 저장된 1950년생 기준이라 청소년 요금제는 거부
        assertError(() -> service.subscribe(userId, planId("FIVE_G_YOUTH"), LocalDate.of(2010, 1, 1)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        service.subscribe(userId, planId("FIVE_G_SENIOR"), null);
    }

    @Test
    void 개통_시각_전까지는_현재_요금제가_없고_정각부터_현재_요금제가_된다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_LITE"), null).subscription();

        SubscriptionSnapshot before = stateReader.read(subscription, OCT_13_0900_KST.minusSeconds(1));
        assertThat(before.currentPlan()).isNull();
        assertThat(before.pendingActivation()).isNotNull();

        SubscriptionSnapshot after = stateReader.read(subscription, OCT_13_0900_KST);
        assertThat(after.currentPlan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(after.pendingActivation()).isNull();
    }

    @Test
    void 저녁이나_새벽에_신청해도_다음_날_09시에_개통된다() {
        at(Instant.parse("2026-10-12T12:00:00Z")); // 10월 12일 21시(KST)
        SubscriptionSnapshot night = service.subscribe(userId, planId("FIVE_G_LITE"), null);
        assertThat(night.pendingActivation().effectiveFrom()).isEqualTo(OCT_13_0900_KST);

        service.cancel(userId);
        at(Instant.parse("2026-10-11T15:30:00Z")); // 10월 12일 0시 30분(KST)
        SubscriptionSnapshot dawn = service.subscribe(userId, planId("FIVE_G_LITE"), null);
        assertThat(dawn.pendingActivation().effectiveFrom()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
    }

    @Test
    void 월말에_가입해_다음_달이_됐어도_개통_전이면_변경할_수_없다() {
        at(Instant.parse("2026-10-31T10:00:00Z")); // 10월 31일 19시(KST), 개통은 11월 1일 09시
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        at(Instant.parse("2026-10-31T16:00:00Z")); // 11월 1일 1시(KST)
        assertError(() -> service.changePlan(userId, planId("FIVE_G_STANDARD"), null),
                SubscriptionErrorCode.CHANGE_NOT_ALLOWED_BEFORE_ACTIVATION);

        at(Instant.parse("2026-11-01T00:00:00Z")); // 11월 1일 9시(KST) 개통 직후
        assertThat(service.changePlan(userId, planId("FIVE_G_STANDARD"), null).pendingChange()).isNotNull();
    }

    @Test
    void 개통_전에도_해지할_수_있고_신청한_요금제가_해지_당시_요금제로_남는다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_LITE"), null).subscription();

        service.cancel(userId);

        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isEmpty();
        SubscriptionHistory last = historyOf(subscription).get(1);
        assertThat(last.getType()).isEqualTo(SubscriptionHistory.Type.CANCEL);
        assertThat(last.getFromPlanId()).isEqualTo(planId("FIVE_G_LITE"));
    }

    @Test
    void 가입한_달에는_요금제를_변경할_수_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        at(Instant.parse("2026-10-31T14:00:00Z")); // 10월 31일 23시(KST)
        assertError(() -> service.changePlan(userId, planId("FIVE_G_STANDARD"), null),
                SubscriptionErrorCode.CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH);
    }

    @Test
    void 가입_다음_달에는_변경할_수_있고_신청_다음_날_0시부터_적용된다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        Instant requested = Instant.parse("2026-11-10T03:00:00Z"); // 11월 10일 정오(KST)
        at(requested);
        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("FIVE_G_STANDARD"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(snapshot.pendingChange().plan().getCode()).isEqualTo("FIVE_G_STANDARD");
        Instant effective = Instant.parse("2026-11-10T15:00:00Z"); // 11월 11일 0시(KST)
        assertThat(snapshot.pendingChange().effectiveFrom()).isEqualTo(effective);

        Subscription subscription = snapshot.subscription();
        assertThat(stateReader.read(subscription, effective.minusSeconds(1)).currentPlan().getCode())
                .isEqualTo("FIVE_G_LITE");
        SubscriptionSnapshot applied = stateReader.read(subscription, effective);
        assertThat(applied.currentPlan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(applied.pendingChange()).isNull();
    }

    @Test
    void 같은_달에_두_번_변경할_수_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-10T03:00:00Z"));
        service.changePlan(userId, planId("FIVE_G_STANDARD"), null);

        assertError(() -> service.changePlan(userId, planId("LTE_PLUS"), null),
                SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED);
        at(Instant.parse("2026-11-20T03:00:00Z"));
        assertError(() -> service.changePlan(userId, planId("LTE_PLUS"), null),
                SubscriptionErrorCode.MONTHLY_CHANGE_LIMIT_EXCEEDED);
    }

    @Test
    void 월말에_신청한_변경은_그_달로_세고_다음_달에는_다시_변경할_수_있다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-30T05:00:00Z")); // 11월 30일 14시(KST)
        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("FIVE_G_STANDARD"), null);
        assertThat(snapshot.pendingChange().effectiveFrom()).isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));

        at(Instant.parse("2026-12-01T00:00:00Z")); // 12월 1일 9시(KST)
        SubscriptionSnapshot december = service.changePlan(userId, planId("LTE_PLUS"), null);

        assertThat(december.currentPlan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(december.pendingChange().plan().getCode()).isEqualTo("LTE_PLUS");
    }

    @Test
    void 현재_요금제와_같은_요금제로는_변경할_수_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-10T03:00:00Z"));

        assertError(() -> service.changePlan(userId, planId("FIVE_G_LITE"), null), SubscriptionErrorCode.SAME_PLAN);
    }

    @Test
    void 변경할_때도_청소년_시니어_자격을_확인하고_거부되면_변경_횟수를_쓰지_않는다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-10T03:00:00Z"));

        assertError(() -> service.changePlan(userId, planId("FIVE_G_SENIOR"), LocalDate.of(1990, 1, 1)),
                SubscriptionErrorCode.AGE_NOT_ELIGIBLE);
        assertError(() -> service.changePlan(userId, planId("FIVE_G_SENIOR"), null),
                SubscriptionErrorCode.BIRTH_DATE_REQUIRED);

        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("FIVE_G_STANDARD"), null);
        assertThat(snapshot.pendingChange().plan().getCode()).isEqualTo("FIVE_G_STANDARD");
    }

    @Test
    void 가입하지_않았거나_해지한_사용자는_변경과_해지를_할_수_없다() {
        assertError(() -> service.changePlan(userId, planId("FIVE_G_LITE"), null),
                SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);
        assertError(() -> service.cancel(userId), SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void 해지는_즉시_처리되고_해지_당시_요금제가_이력에_남는다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_LITE"), null).subscription();
        at(Instant.parse("2026-11-10T03:00:00Z"));
        service.changePlan(userId, planId("FIVE_G_STANDARD"), null);

        Instant canceledAt = Instant.parse("2026-11-12T03:00:00Z");
        at(canceledAt);
        service.cancel(userId);

        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isEmpty();
        assertThat(subscriptionRepository.findById(subscription.getSubscriptionId()).orElseThrow().getCanceledAt())
                .isEqualTo(canceledAt);
        SubscriptionHistory last = historyOf(subscription).get(2);
        assertThat(last.getType()).isEqualTo(SubscriptionHistory.Type.CANCEL);
        assertThat(last.getFromPlanId()).isEqualTo(planId("FIVE_G_STANDARD"));
        assertThat(last.getEffectiveFrom()).isEqualTo(canceledAt);
    }

    @Test
    void 해지_후_재가입하면_새_구독이고_가입_당월_변경_제한이_다시_적용된다() {
        Subscription first = service.subscribe(userId, planId("FIVE_G_LITE"), null).subscription();
        at(Instant.parse("2026-11-10T03:00:00Z"));
        service.cancel(userId);

        Subscription second = service.subscribe(userId, planId("LTE_BASIC"), null).subscription();

        assertThat(second.getSubscriptionId()).isNotEqualTo(first.getSubscriptionId());
        assertError(() -> service.changePlan(userId, planId("LTE_PLUS"), null),
                SubscriptionErrorCode.CHANGE_NOT_ALLOWED_IN_SUBSCRIBED_MONTH);
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }

    private LocalDate birthDateOf(Long id) {
        return userRepository.findById(id).orElseThrow().getBirthDate();
    }

    private List<SubscriptionHistory> historyOf(Subscription subscription) {
        return historyRepository.findAll().stream()
                .filter(history -> history.getSubscriptionId().equals(subscription.getSubscriptionId()))
                .sorted((a, b) -> a.getHistoryId().compareTo(b.getHistoryId()))
                .toList();
    }

    private void assertError(Runnable action, BaseErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
