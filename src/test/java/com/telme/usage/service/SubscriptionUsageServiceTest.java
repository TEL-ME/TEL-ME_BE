package com.telme.usage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionPauseService;
import com.telme.usage.repository.SubscriptionUsageRepository;
import jakarta.persistence.EntityManager;
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

// 가입 개통은 신청 다음 날 09시(KST)이므로 사용량은 그 이후의 달부터 생긴다
@SpringBootTest
@TestPropertySource(properties = "telme.subscription.demo-mode=false")
@Transactional
class SubscriptionUsageServiceTest {

    // 2026-01-05(월) 정오(KST) 가입, 1월 6일 9시 개통
    static final Instant JANUARY_JOIN = Instant.parse("2026-01-05T03:00:00Z");
    // 2026-10-12(월) 정오(KST)
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");
    static final LocalDate OCT = LocalDate.of(2026, 10, 1);

    @MockitoBean Clock clock;

    @Autowired SubscriptionUsageService usageService;
    @Autowired SubscriptionCommandService commandService;
    @Autowired SubscriptionPauseService pauseService;
    @Autowired SubscriptionUsageRepository usageRepository;
    @Autowired UsageGenerator generator;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("usage-svc@test.com").name("사용량").build()).getUserId();
    }

    @Test
    void 가입하지_않았으면_미가입_상태이고_사용량은_없다() {
        at(OCT_12);

        UsageOverview overview = usageService.getOverview(userId);

        assertThat(overview.status()).isEqualTo(UsageOverview.Status.NOT_SUBSCRIBED);
        assertThat(overview.usage()).isNull();
        assertThat(usageService.getRecentMonths(userId, 3)).isEmpty();
    }

    @Test
    void 개통_전에는_개통_예정_시각만_알려주고_사용량을_만들지_않는다() {
        at(OCT_12);
        Long subscriptionId = subscribe("FIVE_G_STANDARD", false).subscriptionId;

        UsageOverview overview = usageService.getOverview(userId);

        assertThat(overview.status()).isEqualTo(UsageOverview.Status.PENDING_ACTIVATION);
        assertThat(overview.activatesAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(overview.usage()).isNull();
        assertThat(usageRepository.findFromMonth(subscriptionId, LocalDate.of(2020, 1, 1))).isEmpty();
        assertThat(usageService.getRecentMonths(userId, 3)).isEmpty();
    }

    @Test
    void 이번_달_사용량은_지난_일수만큼만_반영하고_정산일까지_남은_일수를_알려준다() {
        at(Instant.parse("2026-09-10T03:00:00Z"));
        subscribe("FIVE_G_STANDARD", false);
        at(OCT_12);

        ThisMonthUsage usage = usageService.getOverview(userId).usage();

        GeneratedUsage full = generator.generate(userId, OCT);
        assertThat(usage.month()).isEqualTo(OCT);
        assertThat(usage.daysUntilBilling()).isEqualTo(19); // 31일 - 12일
        assertThat(usage.data().used()).isEqualTo(Math.round(full.dataMb() * 12 / 31.0));
        assertThat(usage.voice().used()).isEqualTo(Math.round(full.voiceMinutes() * 12 / 31.0));
        assertThat(usage.sms().used()).isEqualTo(Math.round(full.smsCount() * 12 / 31.0));
        assertThat(usage.paused()).isFalse();
    }

    @Test
    void 말일에는_정산일까지_0일이고_한_달_전체_값이다() {
        at(JANUARY_JOIN);
        subscribe("FIVE_G_STANDARD", false);
        at(Instant.parse("2026-10-31T03:00:00Z"));

        ThisMonthUsage usage = usageService.getOverview(userId).usage();

        assertThat(usage.daysUntilBilling()).isZero();
        assertThat(usage.data().used()).isEqualTo(generator.generate(userId, OCT).dataMb());
    }

    @Test
    void 개통한_달은_개통_이후_일수만큼만_반영하고_개통_전_달은_만들지_않는다() {
        at(Instant.parse("2026-09-10T03:00:00Z")); // 9월 11일 9시 개통
        subscribe("FIVE_G_STANDARD", false);
        at(OCT_12);

        List<MonthlyUsage> recent = usageService.getRecentMonths(userId, 3);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).month()).isEqualTo(LocalDate.of(2026, 9, 1));
        // 9월은 11일부터 30일까지 20일
        assertThat(recent.get(0).dataMb()).isEqualTo(
                Math.round(generator.generate(userId, LocalDate.of(2026, 9, 1)).dataMb() * 20 / 30.0));
    }

    @Test
    void 개통한_지_오래되면_지난_3개월과_이번_달만_만들고_다시_조회해도_늘지_않는다() {
        Long subscriptionId = subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        usageService.getOverview(userId);
        usageService.getOverview(userId);
        List<MonthlyUsage> recent = usageService.getRecentMonths(userId, 3);

        assertThat(usageRepository.findFromMonth(subscriptionId, LocalDate.of(2020, 1, 1))).hasSize(4);
        assertThat(recent).extracting(MonthlyUsage::month)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(recent.get(0).dataMb()).isEqualTo(generator.generate(userId, LocalDate.of(2026, 7, 1)).dataMb());
        assertThat(usageService.getRecentMonths(userId, 2)).hasSize(2);
        assertThat(usageService.getRecentMonths(userId, 10)).hasSize(3);
        assertThat(usageService.getRecentMonths(userId, 0)).isEmpty();
    }

    @Test
    void 한_번_만든_값은_저장되어_다시_만들지_않는다() {
        Long subscriptionId = subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-10-31T03:00:00Z"));
        usageService.getOverview(userId);

        jdbc.update("UPDATE subscription_usages SET used_data_mb = 1234 WHERE subscription_id = ? AND usage_month = ?",
                subscriptionId, OCT);
        entityManager.clear();

        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(1234L);
    }

    @Test
    void 한도를_넘기면_사용률이_100을_넘고_초과로_표시되고_남은_양은_0이다() {
        subscribeAt(JANUARY_JOIN, "BUDGET_MINI", false); // 1.5GB, 통화 300분, 문자 100건
        at(Instant.parse("2026-10-31T03:00:00Z"));

        ThisMonthUsage usage = usageService.getOverview(userId).usage();

        GeneratedUsage full = generator.generate(userId, OCT);
        // 가장 가벼운 유형도 월 1.6GB 이상이라 1.5GB 한도를 넘는다
        assertThat(usage.data().limit()).isEqualTo(1536L);
        assertThat(usage.data().exceeded()).isTrue();
        assertThat(usage.data().remaining()).isZero();
        assertThat(usage.data().percent()).isEqualTo((int) Math.round(full.dataMb() * 100.0 / 1536));
        assertThat(usage.data().percent()).isGreaterThan(100);
        assertThat(usage.voice().limit()).isEqualTo(300L);
        assertThat(usage.voice().exceeded()).isEqualTo(full.voiceMinutes() > 300);
        assertThat(usage.voice().remaining()).isEqualTo(Math.max(0, 300 - full.voiceMinutes()));
        assertThat(usage.sms().limit()).isEqualTo(100L);
        assertThat(usage.sms().percent()).isEqualTo((int) Math.round(full.smsCount() * 100.0 / 100));
    }

    @Test
    void 무제한_항목은_한도와_사용률이_없고_초과되지_않는다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_UNLIMITED", false);
        at(Instant.parse("2026-10-31T03:00:00Z"));

        ThisMonthUsage usage = usageService.getOverview(userId).usage();

        for (ResourceUsage resource : List.of(usage.data(), usage.voice(), usage.sms())) {
            assertThat(resource.unlimited()).isTrue();
            assertThat(resource.limit()).isNull();
            assertThat(resource.remaining()).isNull();
            assertThat(resource.percent()).isNull();
            assertThat(resource.exceeded()).isFalse();
        }
    }

    @Test
    void 데이터는_한도형이고_통화와_문자는_무제한인_요금제도_항목별로_구분한다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false); // 50GB, 통화·문자 무제한
        at(Instant.parse("2026-10-31T03:00:00Z"));

        ThisMonthUsage usage = usageService.getOverview(userId).usage();

        assertThat(usage.data().limit()).isEqualTo(50L * 1024);
        assertThat(usage.data().unlimited()).isFalse();
        assertThat(usage.voice().unlimited()).isTrue();
        assertThat(usage.sms().unlimited()).isTrue();
    }

    @Test
    void 약정이_없으면_예상_요금은_월정액이고_전월과_같으면_차이가_0이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        ExpectedFee fee = usageService.getOverview(userId).usage().expectedFee();

        assertThat(fee.amount()).isEqualTo(65_000);
        assertThat(fee.previousAmount()).isEqualTo(65_000);
        assertThat(fee.difference()).isZero();
    }

    @Test
    void 약정_중이면_예상_요금에서_월정액의_25퍼센트를_뺀다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", true);
        at(OCT_12);

        ExpectedFee fee = usageService.getOverview(userId).usage().expectedFee();

        assertThat(fee.amount()).isEqualTo(48_750);
        assertThat(fee.previousAmount()).isEqualTo(48_750);
        assertThat(fee.difference()).isZero();
    }

    @Test
    void 약정이_만료된_달부터는_할인이_빠져_전월보다_오른다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", true);
        at(Instant.parse("2028-02-12T03:00:00Z")); // 가입 25개월째, 지난 1월 1일에는 아직 23개월

        ExpectedFee fee = usageService.getOverview(userId).usage().expectedFee();

        assertThat(fee.amount()).isEqualTo(65_000);
        assertThat(fee.previousAmount()).isEqualTo(48_750);
        assertThat(fee.difference()).isEqualTo(16_250);
    }

    @Test
    void 요금제를_바꾸면_전월_대비는_지난달_시작_시점의_요금제와_비교한다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-04-10T03:00:00Z"));
        commandService.changePlan(userId, planId("LTE_PLUS"), null); // 4월 11일부터 49,000원
        at(Instant.parse("2026-05-12T03:00:00Z"));

        ExpectedFee fee = usageService.getOverview(userId).usage().expectedFee();

        assertThat(fee.amount()).isEqualTo(49_000);
        assertThat(fee.previousAmount()).isEqualTo(65_000);
        assertThat(fee.difference()).isEqualTo(-16_000);
    }

    @Test
    void 이번_달에_개통했으면_전월_대비가_없다() {
        at(Instant.parse("2026-10-01T03:00:00Z")); // 10월 2일 9시 개통
        subscribe("FIVE_G_STANDARD", false);
        at(OCT_12);

        ExpectedFee fee = usageService.getOverview(userId).usage().expectedFee();

        assertThat(fee.amount()).isEqualTo(65_000);
        assertThat(fee.previousAmount()).isNull();
        assertThat(fee.difference()).isNull();
    }

    @Test
    void 일시_정지_중이어도_사용량_값은_그대로이고_정지_중으로만_표시한다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);
        ThisMonthUsage before = usageService.getOverview(userId).usage();

        pauseService.pause(userId, 5);
        ThisMonthUsage after = usageService.getOverview(userId).usage();

        assertThat(before.paused()).isFalse();
        assertThat(after.paused()).isTrue();
        assertThat(after.data()).isEqualTo(before.data());
        assertThat(after.voice()).isEqualTo(before.voice());
        assertThat(after.sms()).isEqualTo(before.sms());
    }

    @Test
    void 해지하면_미가입_상태가_된다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);
        commandService.cancel(userId);

        assertThat(usageService.getOverview(userId).status()).isEqualTo(UsageOverview.Status.NOT_SUBSCRIBED);
    }

    private Long subscribeAt(Instant joinedAt, String planCode, boolean contract) {
        at(joinedAt);
        return subscribe(planCode, contract).subscriptionId;
    }

    private Subscribed subscribe(String planCode, boolean contract) {
        return new Subscribed(commandService.subscribe(userId, planId(planCode), null, contract)
                .subscription().getSubscriptionId());
    }

    private record Subscribed(Long subscriptionId) {
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
