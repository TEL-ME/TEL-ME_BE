package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.consult.service.MemberPlanAnswerer.MemberAnswer;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.exception.SubscriptionErrorCode;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionPauseService;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.usage.service.SubscriptionUsageService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
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
class MemberPlanAnswererTest {

    // 2026-01-05(월) 정오(KST) 가입, 1월 6일 9시 개통
    static final Instant JANUARY_JOIN = Instant.parse("2026-01-05T03:00:00Z");
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired MemberPlanAnswerer answerer;
    @Autowired SubscriptionCommandService commandService;
    @Autowired SubscriptionPauseService pauseService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionUsageService usageService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("member-plan@test.com").name("회원").build()).getUserId();
        at(OCT_12);
    }

    @Test
    void 로그인하지_않았으면_로그인_안내와_전체_요금제_버튼을_준다() {
        MemberAnswer subscription = answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, null);
        MemberAnswer penalty = answerer.answer(PlanQuestionType.MY_PENALTY, null);

        assertThat(subscription.content()).contains("로그인하시면 가입하신 요금제를 확인해 드릴 수 있어요");
        assertThat(penalty.content()).contains("로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요");
        assertThat(subscription.followUps()).containsExactly("요금제 종류 알려줘");
        assertThat(penalty.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 내_요금제와_위약금과_사용량_질문이_아니면_답하지_않는다() {
        for (PlanQuestionType type : new PlanQuestionType[] {PlanQuestionType.NONE, PlanQuestionType.PLAN_CATALOG,
                PlanQuestionType.RECOMMENDATION}) {
            assertThat(answerer.answer(type, userId)).isNull();
            assertThat(answerer.answer(type, null)).isNull();
        }
    }

    @Test
    void 가입하지_않은_회원에게는_가입_안내와_전체_요금제_버튼을_준다() {
        MemberAnswer subscription = answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId);
        MemberAnswer penalty = answerer.answer(PlanQuestionType.MY_PENALTY, userId);

        assertThat(subscription.content()).contains("아직 가입한 요금제가 없어요");
        assertThat(penalty.content()).contains("가입한 요금제가 없어서 확인할 약정이 없어요");
        assertThat(subscription.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 해지한_회원은_가입하지_않은_회원과_같다() {
        subscribe("FIVE_G_STANDARD", false);
        commandService.cancel(userId);

        assertThat(answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content()).contains("아직 가입한 요금제가 없어요");
        assertThat(answerer.answer(PlanQuestionType.MY_PENALTY, userId).content()).contains("확인할 약정이 없어요");
    }

    @Test
    void 개통_전에는_개통_예정_시각을_알려주고_해지해도_위약금이_없다() {
        subscribe("FIVE_G_STANDARD", true);

        String subscription = answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content();
        String penalty = answerer.answer(PlanQuestionType.MY_PENALTY, userId).content();

        assertThat(subscription).contains("5G 스탠다드 요금제에 가입하셨어요", "10월 13일 오전 9시에 개통될 예정이에요",
                "월정액 65,000원", "데이터 50GB", "통화 ", "문자 ", "약정 24개월 (월 16,250원 할인");
        assertThat(penalty).contains("아직 개통 전이라 받은 할인이 없어서 지금 해지하셔도 위약금은 없어요(0원)");
    }

    @Test
    void 약정_없이_가입한_회원의_내_요금제와_위약금() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-09-05T03:00:00Z"));

        String subscription = answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content();

        assertThat(subscription).contains("지금 가입한 요금제는 5G 스탠다드예요", "월정액 65,000원", "데이터 50GB").doesNotContain("약정");
        assertThat(answerer.answer(PlanQuestionType.MY_PENALTY, userId).content())
                .isEqualTo("약정 없이 가입하셔서 지금 해지하셔도 위약금은 없어요(0원).");
    }

    @Test
    void 약정_8개월째에는_받은_할인액_전부가_위약금이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", true);
        at(Instant.parse("2026-09-05T03:00:00Z"));

        assertThat(answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content())
                .contains("약정 24개월 (월 16,250원 할인, 2028년 1월 5일까지)");
        assertThat(answerer.answer(PlanQuestionType.MY_PENALTY, userId).content())
                .contains("약정 24개월 중 8개월이 지났어요", "약정은 2028년 1월 5일에 끝나요", "위약금은 130,000원이에요",
                        "그동안 받은 할인 130,000원의 100%");
    }

    @Test
    void 약정_12개월이_지나면_받은_할인액의_절반이_위약금이다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", true);
        at(Instant.parse("2027-01-05T03:00:00Z"));

        assertThat(answerer.answer(PlanQuestionType.MY_PENALTY, userId).content())
                .contains("12개월이 지났어요", "위약금은 97,500원이에요", "그동안 받은 할인 195,000원의 50%");
    }

    @Test
    void 약정이_끝나면_할인이_없고_위약금도_없다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", true);
        at(Instant.parse("2028-01-05T03:00:00Z"));

        assertThat(answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content())
                .contains("약정은 2028년 1월 5일에 끝났어요").doesNotContain("할인");
        assertThat(answerer.answer(PlanQuestionType.MY_PENALTY, userId).content())
                .contains("약정이 2028년 1월 5일에 끝나서 지금 해지하셔도 위약금은 없어요(0원)");
    }

    @Test
    void 일시_정지_중이면_정지_기간과_정지_중_기본료를_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-09-05T03:00:00Z"));
        pauseService.pause(userId, 10);

        assertThat(answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content())
                .contains("일시 정지 중이에요 (2026년 9월 15일까지, 정지 중 기본료 32,500원)");
    }

    @Test
    void 요금제_변경이_예약되어_있으면_적용일과_바뀔_요금제를_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-04-10T03:00:00Z"));
        commandService.changePlan(userId, planId("LTE_PLUS"), null);

        assertThat(answerer.answer(PlanQuestionType.MY_SUBSCRIPTION, userId).content())
                .contains("지금 가입한 요금제는 5G 스탠다드예요", "2026년 4월 11일부터 LTE 플러스 요금제로 바뀔 예정이에요");
    }

    @Test
    void 위약금을_계산하는_사이_해지되어도_오류_없이_미가입_안내를_준다() {
        SubscriptionQueryService query = mock(SubscriptionQueryService.class);
        when(query.getMine(anyLong())).thenReturn(SubscriptionMeResponse.of(
                new com.telme.subscription.dto.res.SubscriptionResponse(1L, JANUARY_JOIN, null, null, null, null, null)));
        when(query.getTerminationPenalty(anyLong()))
                .thenThrow(new GeneralException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));

        MemberAnswer answer = new MemberPlanAnswerer(query, mock(SubscriptionUsageService.class)).answer(PlanQuestionType.MY_PENALTY, 1L);

        assertThat(answer.content()).contains("확인할 약정이 없어요");
    }

    @Test
    void 로그인하지_않았거나_가입하지_않았으면_사용량_대신_안내를_준다() {
        MemberAnswer guest = answerer.answer(PlanQuestionType.MY_USAGE, null);
        MemberAnswer member = answerer.answer(PlanQuestionType.MY_USAGE, userId);

        assertThat(guest.content()).contains("로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요");
        assertThat(member.content()).contains("가입한 요금제가 없어서 확인할 사용량이 없어요");
        assertThat(guest.followUps()).containsExactly("요금제 종류 알려줘");
        assertThat(member.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 개통_전에는_사용량이_없고_개통_예정_시각을_알려준다() {
        subscribe("FIVE_G_STANDARD", false);

        assertThat(answerer.answer(PlanQuestionType.MY_USAGE, userId).content())
                .contains("아직 개통 전이라 사용량이 없어요", "10월 13일 오전 9시에 개통될 예정이에요");
    }

    @Test
    void 이번_달_사용량은_사용한_양과_한도와_남은_양과_예상_요금을_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-10-31T14:00:00Z")); // 10월 31일 23시(KST)
        setThisMonthUsage(10 * 1024, 120, 30);

        MemberAnswer answer = answerer.answer(PlanQuestionType.MY_USAGE, userId);

        assertThat(answer.content()).startsWith("10월 사용량이에요. 오늘이 정산일이에요.")
                .contains("- 데이터 10GB / 50GB (남은 양 40GB)", "- 예상 요금 65,000원")
                .doesNotContain("한도를 넘었어요", "일시 정지", "나눠 계산");
        assertThat(answer.followUps()).containsExactly("나한테 맞는 요금제 추천해줘");
    }

    @Test
    void 정산일까지_남은_일수를_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(OCT_12);

        assertThat(answerer.answer(PlanQuestionType.MY_USAGE, userId).content()).contains("정산일까지 19일 남았어요");
    }

    @Test
    void 한도를_넘긴_항목은_넘었다고_알린다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_LITE", false);
        at(Instant.parse("2026-10-31T14:00:00Z"));
        setThisMonthUsage(20 * 1024, 10, 10);

        assertThat(answerer.answer(PlanQuestionType.MY_USAGE, userId).content())
                .contains("- 데이터 20GB / 8GB (남은 양 0GB) - 한도를 넘었어요");
    }

    @Test
    void 요금제를_바꾼_달에는_일할_계산했다고_알리고_지난달과의_차이를_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-10-09T03:00:00Z"));
        commandService.changePlan(userId, planId("FIVE_G_LITE"), null); // 10월 10일부터 적용
        at(Instant.parse("2026-10-20T03:00:00Z"));

        String content = answerer.answer(PlanQuestionType.MY_USAGE, userId).content();

        assertThat(content).contains("이번 달에 요금제가 바뀌어 날짜별로 나눠 계산했어요", "지난달보다", "적어요");
        assertThat(content).doesNotContain("- 예상 요금 65,000원").doesNotContain("- 예상 요금 45,000원");
    }

    @Test
    void 일시_정지_중이면_사용량_답변에_정지_중이라고_알린다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD", false);
        at(Instant.parse("2026-10-20T03:00:00Z"));
        pauseService.pause(userId, 5);

        assertThat(answerer.answer(PlanQuestionType.MY_USAGE, userId).content()).contains("지금은 일시 정지 중이에요");
    }

    private void setThisMonthUsage(long dataMb, int voice, int sms) {
        usageService.getOverview(userId);
        jdbc.update("UPDATE subscription_usages SET used_data_mb = ?, used_voice_minutes = ?, used_sms_count = ? "
                + "WHERE usage_month = '2026-10-01' AND subscription_id IN "
                + "(SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)", dataMb, voice, sms, userId);
        entityManager.clear();
    }

    private void subscribeAt(Instant joinedAt, String planCode, boolean contract) {
        at(joinedAt);
        subscribe(planCode, contract);
    }

    private void subscribe(String planCode, boolean contract) {
        commandService.subscribe(userId, planId(planCode), null, contract);
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
