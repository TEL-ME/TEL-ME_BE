package com.telme.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 약정 가입과 위약금(TERMINATE-02·05). 5G 스탠다드 월 65,000원의 25% 할인은 월 16,250원이다
@SpringBootTest
@TestPropertySource(properties = "telme.subscription.demo-mode=false")
@Transactional
class SubscriptionContractTest {

    // 2026-01-05(월) 정오(KST)
    static final Instant JOINED = Instant.parse("2026-01-05T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired SubscriptionCommandService service;
    @Autowired SubscriptionQueryService queryService;
    @Autowired SubscriptionStateReader stateReader;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionHistoryRepository historyRepository;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("sub-contract-svc@test.com").name("약정").build()).getUserId();
        at(JOINED);
    }

    @Test
    void 약정을_고르면_24개월과_25퍼센트가_구독에_저장된다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true).subscription();

        assertThat(subscription.getContractMonths()).isEqualTo(24);
        assertThat(subscription.getContractDiscountPercent()).isEqualTo(25);
    }

    @Test
    void 약정을_고르지_않으면_약정이_없고_해지해도_위약금은_0원이다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null).subscription();
        assertThat(subscription.hasContract()).isFalse();

        at(Instant.parse("2026-09-05T03:00:00Z"));
        ContractPenalty penalty = service.cancel(userId);

        assertThat(penalty).isEqualTo(ContractPenalty.none());
        assertThat(canceledHistory(subscription).getPenaltyAmount()).isZero();
    }

    @Test
    void 약정_8개월째_해지하면_받은_할인액_전부가_위약금이다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true).subscription();

        at(Instant.parse("2026-09-05T03:00:00Z"));
        ContractPenalty penalty = service.cancel(userId);

        assertThat(penalty.elapsedMonths()).isEqualTo(8);
        assertThat(penalty.receivedDiscount()).isEqualTo(130_000L);
        assertThat(penalty.refundPercent()).isEqualTo(100);
        assertThat(penalty.penalty()).isEqualTo(130_000L);
        assertThat(canceledHistory(subscription).getPenaltyAmount()).isEqualTo(130_000L);
    }

    @Test
    void 해지하기_전에_미리_계산한_위약금은_해지로_확정된_위약금과_같고_상태를_바꾸지_않는다() {
        service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true);
        at(Instant.parse("2026-09-05T03:00:00Z"));

        ContractPenalty preview = queryService.getTerminationPenalty(userId);

        assertThat(subscriptionRepository.findByUserIdAndStatus(userId, Subscription.Status.ACTIVE)).isPresent();
        assertThat(service.cancel(userId)).isEqualTo(preview);
    }

    @Test
    void 위약금_비율은_12개월_전후와_약정_만료에서_바뀐다() {
        service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true);

        assertPenalty("2026-12-05T03:00:00Z", 11, 178_750L, 100, 178_750L);
        // 12개월 하루 전은 아직 11개월
        assertPenalty("2027-01-04T03:00:00Z", 11, 178_750L, 100, 178_750L);
        assertPenalty("2027-01-05T03:00:00Z", 12, 195_000L, 50, 97_500L);
        assertPenalty("2027-07-05T03:00:00Z", 18, 292_500L, 50, 146_250L);
        assertPenalty("2028-01-04T03:00:00Z", 23, 373_750L, 50, 186_875L);
        assertPenalty("2028-01-05T03:00:00Z", 24, 390_000L, 0, 0L);
        // 24개월을 넘겨도 할인액은 약정 기간만 센다
        assertPenalty("2028-06-05T03:00:00Z", 29, 390_000L, 0, 0L);
    }

    @Test
    void 요금제를_바꾼_달부터는_바뀐_월정액으로_받은_할인액을_계산한다() {
        service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true);
        at(Instant.parse("2026-04-10T03:00:00Z"));
        // 신청 다음 날(4월 11일)부터 적용되므로 4월 5일이 속한 달까지는 스탠다드 65,000원
        service.changePlan(userId, planId("LTE_PLUS"), null);

        // 7개월: 스탠다드 4개월 16,250 x 4 + LTE 플러스(49,000원) 3개월 12,250 x 3
        assertPenalty("2026-08-05T03:00:00Z", 7, 101_750L, 100, 101_750L);
    }

    @Test
    void 개통_전에_해지하면_받은_할인이_없어_위약금이_0원이다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true).subscription();

        ContractPenalty penalty = service.cancel(userId);

        assertThat(penalty.elapsedMonths()).isZero();
        assertThat(penalty.receivedDiscount()).isZero();
        assertThat(penalty.penalty()).isZero();
        assertThat(canceledHistory(subscription).getPenaltyAmount()).isZero();
    }

    @Test
    void 약정은_만료일_0시_정각에_끝나고_위약금이_없어지는_순간과_같다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true).subscription();
        // 2026-01-05 가입, 24개월 뒤 2028-01-05 0시(KST)가 만료일
        Instant endsAt = Instant.parse("2028-01-04T15:00:00Z");

        SubscriptionSnapshot.ContractInfo before = contractAt(subscription, "2028-01-04T14:59:59Z");
        assertThat(before.active()).isTrue();
        assertThat(before.endsAt()).isEqualTo(endsAt);
        assertThat(queryService.getTerminationPenalty(userId).refundPercent()).isEqualTo(50);

        SubscriptionSnapshot.ContractInfo after = contractAt(subscription, "2028-01-04T15:00:00Z");
        assertThat(after.active()).isFalse();
        assertThat(after.endsAt()).isEqualTo(endsAt);
        assertThat(queryService.getTerminationPenalty(userId).refundPercent()).isZero();
    }

    @Test
    void 윤일_가입자의_약정은_평년_2월_28일_0시에_끝나고_그날부터_위약금이_없다() {
        at(Instant.parse("2028-02-29T03:00:00Z"));
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true).subscription();

        // 2030-02-28 0시(KST) 직전과 정각
        SubscriptionSnapshot.ContractInfo before = contractAt(subscription, "2030-02-27T14:59:59Z");
        assertThat(before.active()).isTrue();
        assertThat(queryService.getTerminationPenalty(userId).refundPercent()).isEqualTo(50);

        SubscriptionSnapshot.ContractInfo after = contractAt(subscription, "2030-02-27T15:00:00Z");
        assertThat(after.active()).isFalse();
        assertThat(after.endsAt()).isEqualTo(Instant.parse("2030-02-27T15:00:00Z"));
        ContractPenalty penalty = queryService.getTerminationPenalty(userId);
        assertThat(penalty.elapsedMonths()).isEqualTo(24);
        assertThat(penalty.refundPercent()).isZero();
        assertThat(penalty.penalty()).isZero();
    }

    @Test
    void 윤일_가입자의_12개월_위약금_비율은_평년_2월_28일_0시에_바뀐다() {
        at(Instant.parse("2028-02-29T03:00:00Z"));
        service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true);

        assertPenalty("2029-02-27T14:59:59Z", 11, 178_750L, 100, 178_750L);
        assertPenalty("2029-02-27T15:00:00Z", 12, 195_000L, 50, 97_500L);
    }

    @Test
    void 약정이_없는_구독은_약정_정보가_없다() {
        Subscription subscription = service.subscribe(userId, planId("FIVE_G_STANDARD"), null).subscription();

        assertThat(stateReader.read(subscription, Instant.parse("2026-06-01T03:00:00Z")).contract()).isNull();
    }

    @Test
    void 해지_후_다시_가입하면_약정은_새_구독의_선택을_따른다() {
        service.subscribe(userId, planId("FIVE_G_STANDARD"), null, true);
        at(Instant.parse("2026-02-05T03:00:00Z"));
        service.cancel(userId);

        Subscription again = service.subscribe(userId, planId("LTE_BASIC"), null).subscription();

        assertThat(again.hasContract()).isFalse();
    }

    @Test
    void 가입하지_않은_사용자는_위약금을_조회할_수_없다() {
        assertThatThrownBy(() -> queryService.getTerminationPenalty(userId))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private SubscriptionSnapshot.ContractInfo contractAt(Subscription subscription, String at) {
        at(Instant.parse(at));
        return stateReader.read(subscription, Instant.parse(at)).contract();
    }

    private void assertPenalty(String at, int elapsed, long received, int refundPercent, long penalty) {
        at(Instant.parse(at));
        ContractPenalty actual = queryService.getTerminationPenalty(userId);
        assertThat(actual.elapsedMonths()).as("%s 경과 개월", at).isEqualTo(elapsed);
        assertThat(actual.receivedDiscount()).as("%s 받은 할인액", at).isEqualTo(received);
        assertThat(actual.refundPercent()).as("%s 반환 비율", at).isEqualTo(refundPercent);
        assertThat(actual.penalty()).as("%s 위약금", at).isEqualTo(penalty);
    }

    private SubscriptionHistory canceledHistory(Subscription subscription) {
        return historyRepository.findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(subscription.getSubscriptionId())
                .stream()
                .filter(history -> history.getType() == SubscriptionHistory.Type.CANCEL)
                .findFirst().orElseThrow();
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
