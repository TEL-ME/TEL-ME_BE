package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionPause;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionPauseRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = "telme.subscription.demo-mode=false")
@Transactional
class SubscriptionPauseServiceTest {

    // 2026-03-02(월) 정오(KST) 가입, 3월 3일 9시부터 개통
    static final Instant JOINED = Instant.parse("2026-03-02T03:00:00Z");
    static final Instant MAR_10 = Instant.parse("2026-03-10T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired SubscriptionCommandService commandService;
    @Autowired SubscriptionPauseService pauseService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionPauseRepository pauseRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionStateReader stateReader;

    Long userId;
    Long subscriptionId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-pause@test.com").name("정지").build()).getUserId();
        at(JOINED);
        subscriptionId = commandService.subscribe(userId, planId("FIVE_G_STANDARD"), null).subscription().getSubscriptionId();
        at(MAR_10);
    }

    @Test
    void 정지를_신청하면_신청한_일수만큼_정지되고_조회에_정지_중으로_나온다() {
        SubscriptionSnapshot snapshot = pauseService.pause(userId, 30);

        assertThat(snapshot.pause().getStartedAt()).isEqualTo(MAR_10);
        assertThat(snapshot.pause().getEndsAt()).isEqualTo(MAR_10.plus(Duration.ofDays(30)));
        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_STANDARD");
    }

    @Test
    void 정지_일수는_1일_이상_30일_이하만_받는다() {
        assertError(() -> pauseService.pause(userId, 0), SubscriptionErrorCode.INVALID_PAUSE_DAYS);
        assertError(() -> pauseService.pause(userId, 31), SubscriptionErrorCode.INVALID_PAUSE_DAYS);

        assertThat(pauseService.pause(userId, 1).pause()).isNotNull();
    }

    @Test
    void 개통_전에는_정지할_수_없다() {
        at(JOINED);

        assertError(() -> pauseService.pause(userId, 5), SubscriptionErrorCode.PAUSE_NOT_ALLOWED_BEFORE_ACTIVATION);
    }

    @Test
    void 정지_중에는_다시_정지할_수_없고_종료_시각_정각부터는_다시_신청할_수_있다() {
        pauseService.pause(userId, 5);
        Instant end = MAR_10.plus(Duration.ofDays(5));

        at(end.minusSeconds(1));
        assertError(() -> pauseService.pause(userId, 5), SubscriptionErrorCode.ALREADY_PAUSED);

        at(end);
        assertThat(pauseService.pause(userId, 5).pause().getStartedAt()).isEqualTo(end);
    }

    @Test
    void 기간이_끝나면_자동으로_정지에서_복귀하고_해제하면_바로_복귀한다() {
        pauseService.pause(userId, 5);
        Instant end = MAR_10.plus(Duration.ofDays(5));

        at(end);
        assertThat(currentState().pause()).isNull();

        at(MAR_10.plus(Duration.ofDays(1)));
        assertThat(currentState().pause()).isNotNull();
        SubscriptionSnapshot resumed = pauseService.resume(userId);
        assertThat(resumed.pause()).isNull();
        assertThat(pauseService.pause(userId, 3).pause()).isNotNull();
    }

    @Test
    void 정지_중이_아니면_해제할_수_없다() {
        assertError(() -> pauseService.resume(userId), SubscriptionErrorCode.PAUSE_NOT_FOUND);
    }

    @Test
    void 가입하지_않은_사용자는_정지도_해제도_할_수_없다() {
        Long other = userRepository.save(User.builder().email("sub-pause-other@test.com").name("무").build()).getUserId();

        assertError(() -> pauseService.pause(other, 5), SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);
        assertError(() -> pauseService.resume(other), SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void 연간_합산이_90일을_넘으면_거부하고_정확히_90일은_된다() {
        savePause("2026-04-01T00:00:00Z", 30);
        savePause("2026-05-15T00:00:00Z", 30);
        savePause("2026-07-01T00:00:00Z", 29);
        at(Instant.parse("2026-09-01T03:00:00Z"));

        assertError(() -> pauseService.pause(userId, 2), SubscriptionErrorCode.PAUSE_ANNUAL_LIMIT_EXCEEDED);
        assertThat(pauseService.pause(userId, 1).pause()).isNotNull();
    }

    @Test
    void 해제해서_줄어든_일수만큼만_한도를_쓴다() {
        savePause("2026-04-01T00:00:00Z", 30);
        savePause("2026-05-15T00:00:00Z", 30);
        // 세 번째 정지는 30일을 신청했지만 10일 만에 해제
        SubscriptionPause third = savePause("2026-07-01T00:00:00Z", 30);
        third.endAt(third.getStartedAt().plus(Duration.ofDays(10)));
        at(Instant.parse("2026-09-01T03:00:00Z"));

        assertError(() -> pauseService.pause(userId, 21), SubscriptionErrorCode.PAUSE_ANNUAL_LIMIT_EXCEEDED);
        assertThat(pauseService.pause(userId, 20).pause()).isNotNull();
    }

    @Test
    void 연말에_걸친_정지는_각_연도의_한도에_나누어_센다() {
        savePause("2026-04-01T00:00:00Z", 30);
        savePause("2026-05-15T00:00:00Z", 30);
        savePause("2026-07-01T00:00:00Z", 25);

        // 12월 25일 12시(KST)부터 10일: 2026년에 7일(올해 85 + 7 = 92일)이라 거부
        at(Instant.parse("2026-12-25T03:00:00Z"));
        assertError(() -> pauseService.pause(userId, 10), SubscriptionErrorCode.PAUSE_ANNUAL_LIMIT_EXCEEDED);
        assertThat(pauseService.pause(userId, 3).pause()).isNotNull();
    }

    @Test
    void 연말에_걸쳐도_올해_한도에는_올해_몫만_센다() {
        savePause("2026-04-01T00:00:00Z", 30);
        savePause("2026-05-15T00:00:00Z", 30);
        savePause("2026-07-01T00:00:00Z", 25);

        // 12월 30일 12시(KST)부터 10일: 2026년 몫은 2일(85 + 2 = 87일)이고 나머지는 2027년 몫이다
        at(Instant.parse("2026-12-30T03:00:00Z"));
        SubscriptionSnapshot snapshot = pauseService.pause(userId, 10);

        assertThat(snapshot.pause().getEndsAt()).isEqualTo(Instant.parse("2027-01-09T03:00:00Z"));
    }

    @Test
    void 새해에는_한도가_새로_시작한다() {
        savePause("2026-04-01T00:00:00Z", 30);
        savePause("2026-05-15T00:00:00Z", 30);
        savePause("2026-07-01T00:00:00Z", 30);

        at(Instant.parse("2027-01-02T03:00:00Z"));
        assertThat(pauseService.pause(userId, 30).pause()).isNotNull();
    }

    @Test
    void 정지_중에는_요금제를_변경할_수_없고_정지가_끝나면_변경할_수_있다() {
        at(Instant.parse("2026-04-10T03:00:00Z"));
        pauseService.pause(userId, 5);

        assertError(() -> commandService.changePlan(userId, planId("LTE_PLUS"), null),
                SubscriptionErrorCode.CHANGE_NOT_ALLOWED_WHILE_PAUSED);

        at(Instant.parse("2026-04-15T03:00:00Z"));
        assertThat(commandService.changePlan(userId, planId("LTE_PLUS"), null).pendingChange()).isNotNull();
    }

    @Test
    void 정지_중에도_해지할_수_있고_해지하면_정지가_그_시각에_끝난다() {
        pauseService.pause(userId, 30);

        commandService.cancel(userId);

        SubscriptionPause pause = pauseRepository.findAll().stream()
                .filter(p -> p.getSubscriptionId().equals(subscriptionId)).findFirst().orElseThrow();
        assertThat(pause.getEndsAt()).isEqualTo(MAR_10);
        assertThat(pauseRepository.findActive(subscriptionId, MAR_10)).isEmpty();
    }

    private SubscriptionPause savePause(String start, int days) {
        Instant startedAt = Instant.parse(start);
        return pauseRepository.saveAndFlush(
                SubscriptionPause.start(subscriptionId, startedAt, startedAt.plus(Duration.ofDays(days))));
    }

    private SubscriptionSnapshot currentState() {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        return stateReader.read(subscription, clock.instant());
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }

    private void assertError(Runnable action, BaseErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
