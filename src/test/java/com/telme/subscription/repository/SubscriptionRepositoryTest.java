package com.telme.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SubscriptionRepositoryTest {

    static final Instant JOINED = Instant.parse("2026-10-05T01:00:00Z");
    static final Instant CHANGE_REQUESTED = Instant.parse("2026-11-10T03:00:00Z");
    static final Instant CHANGE_EFFECTIVE = Instant.parse("2026-11-10T15:00:00Z");

    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionHistoryRepository historyRepository;

    Long userId;
    Long litePlanId;
    Long standardPlanId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-repo@test.com").name("구독").build()).getUserId();
        litePlanId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("FIVE_G_LITE")).findFirst().orElseThrow().getPlanId();
        standardPlanId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("FIVE_G_STANDARD")).findFirst().orElseThrow().getPlanId();
    }

    @Test
    void 가입하면_활성_구독이_조회되고_해지하면_조회되지_않는다() {
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));

        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isPresent();
        assertThat(subscriptionRepository.findByUserIdAndStatusForUpdate(userId, Subscription.Status.ACTIVE)).isPresent();

        subscription.cancel(CHANGE_REQUESTED);
        subscriptionRepository.flush();

        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isEmpty();
        assertThat(subscriptionRepository.findById(subscription.getSubscriptionId()).orElseThrow().getCanceledAt())
                .isEqualTo(CHANGE_REQUESTED);
    }

    @Test
    void 활성_구독은_사용자당_하나뿐이고_해지_후에는_다시_가입할_수_있다() {
        Subscription first = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 해지_후_재가입은_새_구독으로_저장된다() {
        Subscription first = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));
        first.cancel(CHANGE_REQUESTED);
        subscriptionRepository.flush();

        Subscription second = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, CHANGE_EFFECTIVE));

        assertThat(second.getSubscriptionId()).isNotEqualTo(first.getSubscriptionId());
        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE))
                .get().extracting(Subscription::getSubscriptionId).isEqualTo(second.getSubscriptionId());
    }

    @Test
    void 현재_요금제는_적용_시작이_지난_가장_최근_이력이고_예약된_변경은_따로_조회된다() {
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));
        Long id = subscription.getSubscriptionId();
        historyRepository.save(SubscriptionHistory.subscribed(id, litePlanId, JOINED, JOINED));
        historyRepository.saveAndFlush(
                SubscriptionHistory.changed(id, litePlanId, standardPlanId, CHANGE_EFFECTIVE, CHANGE_REQUESTED));

        assertThat(historyRepository.findCurrent(id, CHANGE_REQUESTED))
                .get().extracting(SubscriptionHistory::getToPlanId).isEqualTo(litePlanId);
        assertThat(historyRepository.findPendingChange(id, CHANGE_REQUESTED))
                .get().extracting(SubscriptionHistory::getToPlanId).isEqualTo(standardPlanId);

        assertThat(historyRepository.findCurrent(id, CHANGE_EFFECTIVE))
                .get().extracting(SubscriptionHistory::getToPlanId).isEqualTo(standardPlanId);
        assertThat(historyRepository.findPendingChange(id, CHANGE_EFFECTIVE)).isEmpty();
    }

    @Test
    void 이번_달_변경_신청_여부는_신청_시각_기준이고_예약된_변경도_센다() {
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));
        Long id = subscription.getSubscriptionId();
        historyRepository.save(SubscriptionHistory.subscribed(id, litePlanId, JOINED, JOINED));
        // 11월 30일 신청 -> 12월 1일 0시(KST) 적용. 신청은 11월이므로 11월 변경 1회로 센다
        Instant requested = Instant.parse("2026-11-30T05:00:00Z");
        Instant effective = Instant.parse("2026-11-30T15:00:00Z");
        historyRepository.saveAndFlush(SubscriptionHistory.changed(id, litePlanId, standardPlanId, effective, requested));

        Instant novemberStart = Instant.parse("2026-10-31T15:00:00Z");
        Instant decemberStart = Instant.parse("2026-11-30T15:00:00Z");
        Instant januaryStart = Instant.parse("2026-12-31T15:00:00Z");

        assertThat(historyRepository.existsChangeRequestedBetween(id, novemberStart, decemberStart)).isTrue();
        assertThat(historyRepository.existsChangeRequestedBetween(id, decemberStart, januaryStart)).isFalse();
    }

    @Test
    void 이력의_요금제_조합이_어긋나면_DB가_거부한다() {
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));

        assertThatThrownBy(() -> historyRepository.saveAndFlush(SubscriptionHistory.changed(
                subscription.getSubscriptionId(), litePlanId, litePlanId, CHANGE_EFFECTIVE, CHANGE_REQUESTED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 생년월일은_비어_있을_때만_채운다() {
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getBirthDate()).isNull();

        user.fillBirthDateIfMissing(LocalDate.of(2010, 5, 1));
        user.fillBirthDateIfMissing(LocalDate.of(1990, 1, 1));
        userRepository.flush();

        assertThat(userRepository.findById(userId).orElseThrow().getBirthDate()).isEqualTo(LocalDate.of(2010, 5, 1));
    }
}
