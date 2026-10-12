package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 시연용 즉시 적용 설정을 켜면 가입·변경이 신청 즉시 적용된다
@SpringBootTest
@TestPropertySource(properties = "telme.subscription.instant-apply=true")
@Transactional
class SubscriptionInstantApplyTest {

    @MockitoBean Clock clock;

    @Autowired SubscriptionCommandService service;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-instant@test.com").name("구독").build()).getUserId();
        at(Instant.parse("2026-10-12T03:00:00Z"));
    }

    @Test
    void 가입은_바로_개통되고_개통_대기가_없다() {
        SubscriptionSnapshot snapshot = service.subscribe(userId, planId("FIVE_G_LITE"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_LITE");
        assertThat(snapshot.pendingActivation()).isNull();
    }

    @Test
    void 변경은_바로_적용되고_예약된_변경이_없다() {
        service.subscribe(userId, planId("FIVE_G_LITE"), null);
        at(Instant.parse("2026-11-10T03:00:00Z"));

        SubscriptionSnapshot snapshot = service.changePlan(userId, planId("FIVE_G_STANDARD"), null);

        assertThat(snapshot.currentPlan().getCode()).isEqualTo("FIVE_G_STANDARD");
        assertThat(snapshot.pendingChange()).isNull();
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
