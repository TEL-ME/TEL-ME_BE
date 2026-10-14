package com.telme.usage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionPauseService;
import com.telme.usage.exception.UsageErrorCode;
import com.telme.usage.repository.SubscriptionUsageRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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

        MonthUsage usage = usageService.getOverview(userId).usage();

        GeneratedUsage full = generated(OCT, "FIVE_G_STANDARD");
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

        MonthUsage usage = usageService.getOverview(userId).usage();

        assertThat(usage.daysUntilBilling()).isZero();
        assertThat(usage.data().used()).isEqualTo(generated(OCT, "FIVE_G_STANDARD").dataMb());
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
                Math.round(generated(LocalDate.of(2026, 9, 1), "FIVE_G_STANDARD").dataMb() * 20 / 30.0));
    }

    @Test
    void 개통한_지_오래되면_지난_6개월과_이번_달만_만들고_다시_조회해도_늘지_않는다() {
        Long subscriptionId = subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        usageService.getOverview(userId);
        usageService.getOverview(userId);

        assertThat(usageRepository.findFromMonth(subscriptionId, LocalDate.of(2020, 1, 1)))
                .extracting(usage -> usage.getUsageMonth())
                .containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), OCT);
    }

    @Test
    void 추천용_최근_달은_이번_달을_뺀_최대_3개월이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        List<MonthlyUsage> recent = usageService.getRecentMonths(userId, 3);

        assertThat(recent).extracting(MonthlyUsage::month)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(recent.get(0).dataMb()).isEqualTo(generated(LocalDate.of(2026, 7, 1), "FIVE_G_STANDARD").dataMb());
        assertThat(usageService.getRecentMonths(userId, 2)).hasSize(2);
        assertThat(usageService.getRecentMonths(userId, 10)).hasSize(3);
        assertThat(usageService.getRecentMonths(userId, 0)).isEmpty();
    }

    @Test
    void 지난_달을_고르면_그달_전체_사용량을_주고_이번_달_전용_값은_없다() {
        subscribeAt(JANUARY_JOIN, "BUDGET_MINI", false); // 1.5GB, 통화 300분, 문자 100건
        at(OCT_12);

        UsageOverview overview = usageService.getOverview(userId, YearMonth.of(2026, 9));

        GeneratedUsage september = generated(LocalDate.of(2026, 9, 1), "BUDGET_MINI");
        MonthUsage usage = overview.usage();
        assertThat(overview.status()).isEqualTo(UsageOverview.Status.ACTIVE);
        assertThat(overview.availableFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(overview.availableTo()).isEqualTo(OCT);
        assertThat(usage.month()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(usage.data().used()).isEqualTo(september.dataMb());
        assertThat(usage.data().limit()).isEqualTo(1536L);
        assertThat(usage.voice().used()).isEqualTo(september.voiceMinutes());
        assertThat(usage.voice().limit()).isEqualTo(300L);
        assertThat(usage.sms().used()).isEqualTo(september.smsCount());
        assertThat(usage.daysUntilBilling()).isNull();
        assertThat(usage.paused()).isNull();
        assertThat(usage.expectedFee()).isNull();
    }

    @Test
    void 월을_생략하거나_이번_달을_고르면_이번_달_값이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        MonthUsage omitted = usageService.getOverview(userId).usage();
        MonthUsage chosen = usageService.getOverview(userId, YearMonth.of(2026, 10)).usage();

        assertThat(chosen).isEqualTo(omitted);
        assertThat(chosen.daysUntilBilling()).isEqualTo(19);
        assertThat(chosen.paused()).isFalse();
        assertThat(chosen.expectedFee()).isNotNull();
    }

    @Test
    void 개통한_달을_고르면_개통_이후_일수만큼만_반영하고_그_이전_달은_조회할_수_없다() {
        at(Instant.parse("2026-09-10T03:00:00Z")); // 9월 11일 9시 개통
        subscribe("FIVE_G_STANDARD", false);
        at(OCT_12);

        UsageOverview overview = usageService.getOverview(userId, YearMonth.of(2026, 9));

        assertThat(overview.availableFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        // 9월은 11일부터 30일까지 20일
        assertThat(overview.usage().data().used()).isEqualTo(
                Math.round(generated(LocalDate.of(2026, 9, 1), "FIVE_G_STANDARD").dataMb() * 20 / 30.0));
        assertThatThrownBy(() -> usageService.getOverview(userId, YearMonth.of(2026, 8)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UsageErrorCode.MONTH_OUT_OF_RANGE));
    }

    @Test
    void 이번_달에_개통했으면_조회할_수_있는_달은_이번_달뿐이다() {
        at(Instant.parse("2026-10-01T03:00:00Z"));
        subscribe("FIVE_G_STANDARD", false);
        at(OCT_12);

        UsageOverview overview = usageService.getOverview(userId);

        assertThat(overview.availableFrom()).isEqualTo(OCT);
        assertThat(overview.availableTo()).isEqualTo(OCT);
        assertThatThrownBy(() -> usageService.getOverview(userId, YearMonth.of(2026, 9)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 여섯_달보다_오래된_달과_미래_달은_조회할_수_없다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        assertThat(usageService.getOverview(userId, YearMonth.of(2026, 4)).usage().month())
                .isEqualTo(LocalDate.of(2026, 4, 1));
        for (YearMonth outOfRange : List.of(YearMonth.of(2026, 3), YearMonth.of(2026, 11), YearMonth.of(2027, 10))) {
            assertThatThrownBy(() -> usageService.getOverview(userId, outOfRange))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(UsageErrorCode.MONTH_OUT_OF_RANGE));
        }
    }

    @Test
    void 미가입이나_개통_전에는_월을_골라도_오류가_아니라_상태를_준다() {
        at(OCT_12);
        assertThat(usageService.getOverview(userId, YearMonth.of(2020, 1)).status())
                .isEqualTo(UsageOverview.Status.NOT_SUBSCRIBED);

        subscribe("FIVE_G_STANDARD", false);

        assertThat(usageService.getOverview(userId, YearMonth.of(2020, 1)).status())
                .isEqualTo(UsageOverview.Status.PENDING_ACTIVATION);
    }

    @Test
    void 요금제를_바꾸기_전_달의_한도는_그때_쓰던_요금제_기준이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-04-10T03:00:00Z"));
        commandService.changePlan(userId, planId("LTE_PLUS"), null); // 4월 11일부터 LTE 플러스
        at(Instant.parse("2026-06-12T03:00:00Z"));

        Long march = usageService.getOverview(userId, YearMonth.of(2026, 3)).usage().data().limit();
        Long april = usageService.getOverview(userId, YearMonth.of(2026, 4)).usage().data().limit();
        Long may = usageService.getOverview(userId, YearMonth.of(2026, 5)).usage().data().limit();

        assertThat(march).isEqualTo(dataLimitMb("FIVE_G_STANDARD"));
        // 4월 1일에는 아직 스탠다드, 5월 1일에는 LTE 플러스
        assertThat(april).isEqualTo(dataLimitMb("FIVE_G_STANDARD"));
        assertThat(may).isEqualTo(dataLimitMb("LTE_PLUS"));
        assertThat(dataLimitMb("FIVE_G_STANDARD")).isNotEqualTo(dataLimitMb("LTE_PLUS"));
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
        Long subscriptionId = subscribeAt(JANUARY_JOIN, "BUDGET_MINI", false); // 1.5GB, 통화 300분, 문자 100건
        at(Instant.parse("2026-10-31T03:00:00Z"));
        usageService.getOverview(userId);
        jdbc.update("UPDATE subscription_usages SET used_data_mb = 3000, used_voice_minutes = 250, used_sms_count = 150 "
                + "WHERE subscription_id = ? AND usage_month = ?", subscriptionId, OCT);
        entityManager.clear();

        MonthUsage usage = usageService.getOverview(userId).usage();

        assertThat(usage.data().limit()).isEqualTo(1536L);
        assertThat(usage.data().exceeded()).isTrue();
        assertThat(usage.data().remaining()).isZero();
        assertThat(usage.data().percent()).isEqualTo(195);
        assertThat(usage.voice().limit()).isEqualTo(300L);
        assertThat(usage.voice().exceeded()).isFalse();
        assertThat(usage.voice().remaining()).isEqualTo(50L);
        assertThat(usage.voice().percent()).isEqualTo(83);
        assertThat(usage.sms().exceeded()).isTrue();
        assertThat(usage.sms().remaining()).isZero();
        assertThat(usage.sms().percent()).isEqualTo(150);
    }

    @Test
    void 생성된_사용량은_그_달_1일에_쓰던_요금제의_제공량에_맞춰진다() {
        Long subscriptionId = subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-04-10T03:00:00Z"));
        commandService.changePlan(userId, planId("LTE_PLUS"), null); // 4월 11일부터 LTE 플러스
        at(Instant.parse("2026-06-12T03:00:00Z"));

        usageService.getOverview(userId);

        // 4월 1일에는 아직 스탠다드, 5월 1일에는 LTE 플러스
        assertThat(storedDataMb(subscriptionId, LocalDate.of(2026, 3, 1)))
                .isEqualTo(generated(LocalDate.of(2026, 3, 1), "FIVE_G_STANDARD").dataMb());
        assertThat(storedDataMb(subscriptionId, LocalDate.of(2026, 4, 1)))
                .isEqualTo(generated(LocalDate.of(2026, 4, 1), "FIVE_G_STANDARD").dataMb());
        assertThat(storedDataMb(subscriptionId, LocalDate.of(2026, 5, 1)))
                .isEqualTo(generated(LocalDate.of(2026, 5, 1), "LTE_PLUS").dataMb());
    }

    @Test
    void 무제한_항목은_한도와_사용률이_없고_초과되지_않는다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_UNLIMITED", false);
        at(Instant.parse("2026-10-31T03:00:00Z"));

        MonthUsage usage = usageService.getOverview(userId).usage();

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

        MonthUsage usage = usageService.getOverview(userId).usage();

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
        MonthUsage before = usageService.getOverview(userId).usage();

        pauseService.pause(userId, 5);
        MonthUsage after = usageService.getOverview(userId).usage();

        assertThat(before.paused()).isFalse();
        assertThat(after.paused()).isTrue();
        assertThat(after.data()).isEqualTo(before.data());
        assertThat(after.voice()).isEqualTo(before.voice());
        assertThat(after.sms()).isEqualTo(before.sms());
    }

    @Test
    void 정지_중에는_날짜가_지나도_사용량이_늘지_않고_복귀하면_이어서_늘어난다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);
        long beforePause = usageService.getOverview(userId).usage().data().used();

        pauseService.pause(userId, 10); // 10월 22일 12시(KST)에 끝남
        at(Instant.parse("2026-10-14T03:00:00Z"));
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(beforePause);
        at(Instant.parse("2026-10-18T03:00:00Z"));
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(beforePause);

        // 13~21일 9일은 정지한 날이라 22일에는 22 - 9 = 13일분
        at(Instant.parse("2026-10-22T03:00:00Z"));
        MonthUsage resumed = usageService.getOverview(userId).usage();
        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.data().used()).isEqualTo(dataUsed(OCT, 13, 31));
        at(Instant.parse("2026-10-25T03:00:00Z"));
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(dataUsed(OCT, 16, 31));
    }

    @Test
    void 정지를_중간에_해제하면_해제한_날부터_다시_늘어난다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);
        pauseService.pause(userId, 10);

        at(Instant.parse("2026-10-15T03:00:00Z"));
        pauseService.resume(userId);

        // 13, 14일만 정지한 날
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(dataUsed(OCT, 13, 31));
        at(Instant.parse("2026-10-16T03:00:00Z"));
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(dataUsed(OCT, 14, 31));
    }

    @Test
    void 달에_걸친_정지는_각_달의_정지한_날만_뺀다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-09-28T03:00:00Z"));
        pauseService.pause(userId, 10); // 10월 8일 12시(KST)에 끝남
        at(OCT_12);

        // 9월은 29, 30일 2일, 10월은 1~7일 7일이 정지한 날
        LocalDate september = LocalDate.of(2026, 9, 1);
        assertThat(usageService.getOverview(userId, YearMonth.of(2026, 9)).usage().data().used())
                .isEqualTo(dataUsed(september, 28, 30));
        assertThat(usageService.getOverview(userId).usage().data().used()).isEqualTo(dataUsed(OCT, 5, 31));
        assertThat(usageService.getRecentMonths(userId, 3)).filteredOn(usage -> usage.month().equals(september))
                .extracting(MonthlyUsage::dataMb).containsExactly(dataUsed(september, 28, 30));
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

    private long storedDataMb(Long subscriptionId, LocalDate month) {
        return usageRepository.findFromMonth(subscriptionId, month).get(0).getUsedDataMb();
    }

    private GeneratedUsage generated(LocalDate month, String planCode) {
        return generator.generate(userId, month, UsageLimits.of(planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(planCode)).findFirst().orElseThrow()));
    }

    private long dataUsed(LocalDate month, int usedDays, int monthDays) {
        return Math.round(generated(month, "FIVE_G_STANDARD").dataMb() * ((double) usedDays / monthDays));
    }

    private Long dataLimitMb(String code) {
        return planRepository.findAll().stream().filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow()
                .getDataGb().multiply(BigDecimal.valueOf(1024)).longValue();
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
