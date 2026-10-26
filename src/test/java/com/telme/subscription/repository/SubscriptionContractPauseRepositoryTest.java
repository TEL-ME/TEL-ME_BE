package com.telme.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.entity.SubscriptionPause;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// 약정·위약금·일시 정지 컬럼과 쿼리를 실제 DB(V32 마이그레이션)로 확인한다. 트랜잭션 롤백으로 흔적을 남기지 않는다
@SpringBootTest
@Transactional
class SubscriptionContractPauseRepositoryTest {

    static final Instant JOINED = Instant.parse("2026-01-05T01:00:00Z");

    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionHistoryRepository historyRepository;
    @Autowired SubscriptionPauseRepository pauseRepository;

    Long userId;
    Long planId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-contract@test.com").name("약정").build()).getUserId();
        planId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("FIVE_G_STANDARD")).findFirst().orElseThrow().getPlanId();
    }

    @Test
    void 약정_정보가_저장되고_약정이_없는_구독은_약정이_없다() {
        Subscription contract = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED, 24, 25));

        Subscription found = subscriptionRepository.findById(contract.getSubscriptionId()).orElseThrow();
        assertThat(found.hasContract()).isTrue();
        assertThat(found.getContractMonths()).isEqualTo(24);
        assertThat(found.getContractDiscountPercent()).isEqualTo(25);

        found.cancel(JOINED.plusSeconds(1));
        subscriptionRepository.flush();
        Subscription plain = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED));
        assertThat(plain.hasContract()).isFalse();
        assertThat(plain.getContractDiscountPercent()).isNull();
    }

    @Test
    void 약정_개월과_할인율_중_한쪽만_있으면_DB가_거부한다() {
        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(
                Subscription.subscribe(userId, JOINED, 24, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 해지_이력에는_위약금이_저장되고_가입_이력에는_DB가_거부한다() {
        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED, 24, 25));
        Long id = subscription.getSubscriptionId();

        SubscriptionHistory withPenalty = historyRepository.saveAndFlush(
                SubscriptionHistory.canceled(id, planId, JOINED.plusSeconds(10), 130_000L));
        SubscriptionHistory noPenalty = historyRepository.saveAndFlush(
                SubscriptionHistory.canceled(id, planId, JOINED.plusSeconds(20), 0L));
        assertThat(historyRepository.findById(withPenalty.getHistoryId()).orElseThrow().getPenaltyAmount())
                .isEqualTo(130_000L);
        assertThat(historyRepository.findById(noPenalty.getHistoryId()).orElseThrow().getPenaltyAmount()).isZero();

        assertThatThrownBy(() -> historyRepository.saveAndFlush(
                SubscriptionHistory.builder().subscriptionId(id).type(SubscriptionHistory.Type.SUBSCRIBE)
                        .toPlanId(planId).effectiveFrom(JOINED).createdAt(JOINED).penaltyAmount(100L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 구독의_이력은_적용_시작_순서로_조회된다() {
        Long id = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)).getSubscriptionId();
        Long otherPlanId = planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals("FIVE_G_LITE")).findFirst().orElseThrow().getPlanId();
        Instant change = JOINED.plusSeconds(86_400L * 40);
        // 일부러 거꾸로 저장해도 적용 시작 순서로 나와야 한다
        historyRepository.save(SubscriptionHistory.changed(id, planId, otherPlanId, change, change));
        historyRepository.saveAndFlush(SubscriptionHistory.subscribed(id, planId, JOINED, JOINED));

        List<SubscriptionHistory> histories = historyRepository.findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(id);

        assertThat(histories).extracting(SubscriptionHistory::getType)
                .containsExactly(SubscriptionHistory.Type.SUBSCRIBE, SubscriptionHistory.Type.CHANGE);
    }

    @Test
    void 정지는_시작_시각부터_종료_시각_직전까지_진행_중이다() {
        Long id = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)).getSubscriptionId();
        Instant start = Instant.parse("2026-03-10T00:00:00Z");
        Instant end = start.plusSeconds(86_400L * 30);
        pauseRepository.saveAndFlush(SubscriptionPause.start(id, start, end));

        assertThat(pauseRepository.findActive(id, start.minusSeconds(1))).isEmpty();
        assertThat(pauseRepository.findActive(id, start)).isPresent();
        assertThat(pauseRepository.findActive(id, end.minusSeconds(1))).isPresent();
        assertThat(pauseRepository.findActive(id, end)).isEmpty();
    }

    @Test
    void 해제하면_종료가_당겨지고_바로_해제해도_저장된다() {
        Long id = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)).getSubscriptionId();
        Instant start = Instant.parse("2026-03-10T00:00:00Z");
        SubscriptionPause pause = pauseRepository.saveAndFlush(
                SubscriptionPause.start(id, start, start.plusSeconds(86_400L * 30)));

        Instant resumed = start.plusSeconds(86_400L * 5);
        pause.endAt(resumed);
        pauseRepository.flush();
        assertThat(pauseRepository.findById(pause.getPauseId()).orElseThrow().getEndsAt()).isEqualTo(resumed);
        assertThat(pauseRepository.findActive(id, resumed)).isEmpty();

        // 이미 끝난 정지는 해제해도 종료가 늘어나지 않는다
        pause.endAt(resumed.plusSeconds(86_400L));
        assertThat(pause.getEndsAt()).isEqualTo(resumed);

        SubscriptionPause instant = pauseRepository.saveAndFlush(
                SubscriptionPause.start(id, start.plusSeconds(86_400L * 40), start.plusSeconds(86_400L * 40)));
        assertThat(instant.getStartedAt()).isEqualTo(instant.getEndsAt());
    }

    @Test
    void 구간과_겹치는_정지만_조회하고_종료_시각은_구간에_포함하지_않는다() {
        Long id = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)).getSubscriptionId();
        Instant year2026 = Instant.parse("2026-01-01T00:00:00Z");
        Instant year2027 = Instant.parse("2027-01-01T00:00:00Z");
        Instant year2028 = Instant.parse("2028-01-01T00:00:00Z");
        // 2026년 12월 25일 ~ 2027년 1월 5일: 두 해에 걸친다
        pauseRepository.save(SubscriptionPause.start(id, Instant.parse("2026-12-25T00:00:00Z"), Instant.parse("2027-01-05T00:00:00Z")));
        // 2026년 안에서만
        pauseRepository.save(SubscriptionPause.start(id, Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-11T00:00:00Z")));
        // 2027년 1월 1일 0시에 끝나는 정지: 2027년과 겹치지 않는다
        pauseRepository.saveAndFlush(SubscriptionPause.start(id, Instant.parse("2026-12-20T00:00:00Z"), year2027));

        assertThat(pauseRepository.findOverlapping(id, year2026, year2027)).hasSize(3);
        assertThat(pauseRepository.findOverlapping(id, year2027, year2028)).hasSize(1);
    }

    @Test
    void 종료가_시작보다_이르면_DB가_거부한다() {
        Long id = subscriptionRepository.saveAndFlush(Subscription.subscribe(userId, JOINED)).getSubscriptionId();
        Instant start = Instant.parse("2026-03-10T00:00:00Z");

        assertThatThrownBy(() -> pauseRepository.saveAndFlush(
                SubscriptionPause.start(id, start, start.minusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
