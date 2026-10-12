package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.exception.SubscriptionErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 시연 모드: 가입·변경 즉시 적용, 가입 당월·월 1회 제한 생략. 요금제 중복·연령 같은 나머지 규칙은 그대로
@SpringBootTest
@TestPropertySource(properties = "telme.subscription.demo-mode=true")
@Transactional
class SubscriptionDemoModeTest {

    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired SubscriptionCommandService service;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-demo@test.com").name("구독").build()).getUserId();
        when(clock.instant()).thenReturn(OCT_12);
    }

    @Test
    void 가입은_바로_개통되고_개통_대기가_없다() {
        SubscriptionSnapshot snapshot = service.subscribe(userId, planId("FIVE_G_LITE"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(snapshot.pendingActivation()).isNull();
    }

    @Test
    void 가입한_달에도_바로_변경되고_예약된_변경이_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("FIVE_G_STANDARD"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(snapshot.pendingChange()).isNull();
    }

    @Test
    void 같은_시각에_여러_번_변경해도_마지막_변경이_현재_요금제다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        service.changePlan(userId, planId("FIVE_G_STANDARD"), null);
        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("LTE_PLUS"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("LTE_PLUS");
    }

    @Test
    void 제한을_생략해도_같은_요금제_변경과_연령_검증은_그대로다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);

        assertThatThrownBy(() -> service.changePlan(userId, planId("FIVE_G_LITE"), null))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SubscriptionErrorCode.SAME_PLAN));
        assertThatThrownBy(() -> service.changePlan(userId, planId("FIVE_G_SENIOR"), LocalDate.of(1990, 1, 1)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SubscriptionErrorCode.AGE_NOT_ELIGIBLE));
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
