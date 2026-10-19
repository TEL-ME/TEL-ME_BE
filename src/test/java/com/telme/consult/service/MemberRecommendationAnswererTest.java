package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.MemberRecommendationAnswerer.Outcome;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.usage.service.SubscriptionUsageService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
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
class MemberRecommendationAnswererTest {

    static final long GB = 1024;
    static final String GENERAL = "만 19~64세";
    // 2026-01-05(월) 정오(KST) 가입, 1월 6일 9시 개통
    static final Instant JANUARY_JOIN = Instant.parse("2026-01-05T03:00:00Z");
    static final Instant OCT_12 = Instant.parse("2026-10-12T03:00:00Z");

    @MockitoBean Clock clock;

    @Autowired MemberRecommendationAnswerer answerer;
    @Autowired SubscriptionCommandService commandService;
    @Autowired SubscriptionUsageService usageService;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder().email("recommend@test.com").name("추천").build()).getUserId();
        at(OCT_12);
    }

    @Test
    void 사용량을_모르는_비회원은_나이대와_데이터_사용량을_순서대로_되묻는다() {
        Outcome outcome = answerer.recommend(null, Map.of(), true);

        assertThat(outcome.content()).isNull();
        assertThat(outcome.asks()).extracting(MissingCondition::key).containsExactly("age_group", "monthly_data_usage");
        assertThat(outcome.asks().get(1).options()).isEmpty();
    }

    @Test
    void 가입하지_않은_회원과_개통_전_회원도_사용량이_없으니_되묻는다() {
        assertThat(answerer.recommend(userId, Map.of(), true).asks())
                .extracting(MissingCondition::key).containsExactly("age_group", "monthly_data_usage");

        commandService.subscribe(userId, planId("FIVE_G_STANDARD"), null);

        assertThat(answerer.recommend(userId, Map.of(), true).asks())
                .extracting(MissingCondition::key).containsExactly("age_group", "monthly_data_usage");
    }

    @Test
    void 생년월일이_있으면_나이대는_묻지_않고_데이터_사용량만_묻는다() {
        jdbc.update("UPDATE users SET birth_date = '1990-05-01' WHERE user_id = ?", userId);
        entityManager.clear();

        assertThat(answerer.recommend(userId, Map.of(), true).asks())
                .extracting(MissingCondition::key).containsExactly("monthly_data_usage");
    }

    @Test
    void 나이대와_말한_사용량으로_여유를_둔_요금제를_추천한다() {
        assertThat(answered(null, "만 14~18세", "50GB"))
                .contains("한 달에 데이터를 50GB 쓰신다면 5G 청소년(월 55,000원, 데이터 80GB) 요금제를 추천해요");
        assertThat(answered(null, "만 14~18세", "3GB"))
                .contains("한 달에 데이터를 3GB 쓰신다면 알뜰 청소년(월 20,000원, 데이터 5GB) 요금제를 추천해요");
        assertThat(answered(null, "만 65세 이상", "30GB"))
                .contains("한 달에 데이터를 30GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요");
        assertThat(answered(null, "만 19~64세", "200GB"))
                .contains("한 달에 데이터를 200GB 쓰신다면 5G 언리미티드(월 105,000원, 데이터 무제한) 요금제를 추천해요");
        assertThat(answered(null, "만 19~64세", "무제한"))
                .contains("데이터를 무제한으로 쓰신다면 5G 언리미티드(월 105,000원, 데이터 무제한) 요금제를 추천해요");
    }

    @Test
    void 한도에_가까운_사용량이면_모자랄_수_있다고_알린다() {
        assertThat(answered(null, GENERAL, "40GB")).contains("한도에 가까워서 사용량이 늘면 모자랄 수 있어요");
        assertThat(answered(null, GENERAL, "30GB")).doesNotContain("한도에 가까워서");
        assertThat(answered(null, GENERAL, "무제한")).doesNotContain("한도에 가까워서");
    }

    @Test
    void 가장_싼_요금제가_LTE면_5G_중_가장_싼_요금제도_함께_안내한다() {
        assertThat(answered(null, GENERAL, "45GB"))
                .contains("LTE 맥스(월 69,000원, 데이터 100GB) 요금제를 추천해요")
                .contains("5G로 쓰시려면 5G 프리미엄(월 85,000원, 데이터 150GB) 요금제가 가장 저렴해요");
        assertThat(answered(null, GENERAL, "40GB")).doesNotContain("5G로 쓰시려면");
        // 알뜰 요금제는 5G 후보를 붙이지 않는다
        assertThat(answered(null, GENERAL, "1GB")).contains("알뜰 미니").doesNotContain("5G로 쓰시려면");
    }

    @Test
    void 숫자_없이_많다고만_답하면_다시_묻고_되묻지_못하면_안내한다() {
        Map<String, String> vague = Map.of("age_group", GENERAL, "monthly_data_usage", "많이 써요");

        assertThat(answerer.recommend(null, vague, true).asks())
                .extracting(MissingCondition::key).containsExactly("monthly_data_usage");
        assertThat(answerer.recommend(null, vague, false).content()).contains("사용량을 알려주시면");
    }

    @Test
    void 사용량을_모른다고_답하면_되묻지_않고_가입_뒤_추천을_안내한다() {
        Outcome outcome = answerer.recommend(null, Map.of("monthly_data_usage", "모름"), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("사용량을 잘 모르시면");
        assertThat(outcome.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 추천한_요금제에_통화나_문자_한도가_있으면_그_항목을_되묻는다() {
        // 1GB는 통화 300분, 문자 100건 한도가 있는 알뜰 미니
        Outcome both = answerer.recommend(null, Map.of("age_group", GENERAL, "monthly_data_usage", "1GB"), true);
        assertThat(both.content()).isNull();
        assertThat(both.asks()).extracting(MissingCondition::key).containsExactly("monthly_voice_usage", "monthly_sms_usage");
        assertThat(both.asks().get(0).options()).isEmpty();

        // 4GB 이하 구간의 LTE 베이직은 통화만 한도가 있다
        Outcome voiceOnly = answerer.recommend(null, Map.of("age_group", GENERAL, "monthly_data_usage", "3GB"), true);
        assertThat(voiceOnly.asks()).extracting(MissingCondition::key).containsExactly("monthly_voice_usage");
    }

    @Test
    void 통화나_문자_한도가_없는_요금제를_추천하면_묻지_않는다() {
        Outcome outcome = answerer.recommend(null, Map.of("age_group", GENERAL, "monthly_data_usage", "40GB"), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("5G 스탠다드").doesNotContain("통화·문자를 많이 쓰신다면");
    }

    @Test
    void 통화량을_말하면_그_한도를_채우는_요금제로_올려_추천한다() {
        Map<String, String> confirmed = Map.of("age_group", GENERAL, "monthly_data_usage", "1GB",
                "monthly_voice_usage", "600분", "monthly_sms_usage", "거의 없음");

        Outcome outcome = answerer.recommend(null, confirmed, true);

        // 통화 600분의 1.2배는 700분 한도의 LTE 베이직도 넘어 통화 무제한인 5G 라이트가 된다
        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("5G 라이트(월 45,000원, 데이터 8GB)").doesNotContain("통화·문자를 많이 쓰신다면");
    }

    @Test
    void 통화량이_적으면_한도가_있는_요금제를_그대로_추천한다() {
        Map<String, String> confirmed = Map.of("age_group", GENERAL, "monthly_data_usage", "1GB",
                "monthly_voice_usage", "100분", "monthly_sms_usage", "20건");

        Outcome outcome = answerer.recommend(null, confirmed, true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("알뜰 미니(월 15,000원, 데이터 1.5GB)");
    }

    @Test
    void 통화나_문자를_모르거나_되묻지_못하면_한도를_알리고_추천한다() {
        Map<String, String> unsure = Map.of("age_group", GENERAL, "monthly_data_usage", "1GB",
                "monthly_voice_usage", "모름", "monthly_sms_usage", "모름");
        assertThat(answerer.recommend(null, unsure, true).content())
                .contains("알뜰 미니").contains("이 요금제는 통화 300분, 문자 100건까지 쓸 수 있으니");

        Map<String, String> unasked = Map.of("age_group", GENERAL, "monthly_data_usage", "3GB");
        Outcome outcome = answerer.recommend(null, unasked, false);
        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("LTE 베이직").contains("이 요금제는 통화 700분까지 쓸 수 있으니");
    }

    @Test
    void 숫자_없이_많이_한다고만_답하면_통화를_다시_묻는다() {
        Map<String, String> confirmed = Map.of("age_group", GENERAL, "monthly_data_usage", "3GB", "monthly_voice_usage", "많이 해요");

        assertThat(answerer.recommend(null, confirmed, true).asks())
                .extracting(MissingCondition::key).containsExactly("monthly_voice_usage");
    }

    @Test
    void 비회원_답변에는_로그인_안내를_회원_답변에는_사용량_안내를_붙인다() {
        assertThat(answered(null, GENERAL, "40GB")).contains("로그인하고 요금제에 가입하시면");
        assertThat(answered(userId, GENERAL, "40GB")).contains("가입한 요금제의 사용량이 쌓이면");
        assertThat(answerer.recommend(null, Map.of("age_group", GENERAL, "monthly_data_usage", "40GB"), false).followUps())
                .containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 데이터_사용량_답이_없으면_되묻지_못하는_경로에서_추천하지_않고_전체_요금제를_안내한다() {
        Outcome outcome = answerer.recommend(null, Map.of(), false);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("사용량을 알려주시면");
        assertThat(outcome.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 같은_채팅방에서_앞서_한_답은_다시_묻지_않고_쓰며_썼다고_알린다() {
        Map<String, String> previous = Map.of("age_group", "만 65세 이상", "monthly_data_usage", "3GB", "monthly_sms_usage", "50건");

        Outcome outcome = answerer.recommend(null, Map.of(), previous, true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("LTE 시니어(월 29,000원, 데이터 8GB)")
                .endsWith("앞서 알려주신 나이대와 데이터 사용량 답을 기준으로 했어요.");
    }

    @Test
    void 이번에_한_답이_앞서_한_답보다_우선하고_이번_답은_앞서_썼다고_알리지_않는다() {
        Map<String, String> previous = Map.of("age_group", "만 65세 이상", "monthly_data_usage", "3GB");

        String content = answerer.recommend(null, Map.of("age_group", "만 14~18세", "monthly_data_usage", "50GB"), previous, false)
                .content();

        assertThat(content).contains("5G 청소년(월 55,000원").doesNotContain("앞서 알려주신");
    }

    @Test
    void 일부만_앞서_답했으면_나머지만_되묻고_쓴_것만_알린다() {
        Outcome ask = answerer.recommend(null, Map.of(), Map.of("age_group", GENERAL), true);
        assertThat(ask.asks()).extracting(MissingCondition::key).containsExactly("monthly_data_usage");

        String content = answerer.recommend(null, Map.of("monthly_data_usage", "40GB"), Map.of("age_group", GENERAL), false).content();
        assertThat(content).endsWith("앞서 알려주신 나이대 답을 기준으로 했어요.");
    }

    @Test
    void 생년월일이_있으면_앞서_한_나이대_답은_쓰지_않아서_썼다고_알리지_않는다() {
        jdbc.update("UPDATE users SET birth_date = '1990-05-01' WHERE user_id = ?", userId);
        entityManager.clear();

        String content = answerer.recommend(userId, Map.of("monthly_data_usage", "40GB"), Map.of("age_group", "만 65세 이상"), false)
                .content();

        assertThat(content).doesNotContain("시니어").doesNotContain("앞서 알려주신");
    }

    @Test
    void 사용량이_있는_회원에게는_앞서_한_데이터_사용량_답을_썼다고_알리지_않는다() {
        subscribeAndUse("FIVE_G_STANDARD", 20 * GB, 100, 50);

        String content = answerer.recommend(userId, Map.of(), Map.of("age_group", GENERAL, "monthly_data_usage", "40GB"), true).content();

        assertThat(content).endsWith("앞서 알려주신 나이대 답을 기준으로 했어요.");
    }

    @Test
    void 이미_답한_항목은_다시_묻지_않는다() {
        assertThat(answerer.recommend(null, Map.of("age_group", GENERAL), true).asks())
                .extracting(MissingCondition::key).containsExactly("monthly_data_usage");
        assertThat(answerer.recommend(null, Map.of("monthly_data_usage", "40GB"), true).asks())
                .extracting(MissingCondition::key).containsExactly("age_group");
    }

    @Test
    void 사용량보다_큰_요금제를_쓰면_더_싼_요금제로_바꾸라고_하고_절약액을_알려준다() {
        subscribeAndUse("FIVE_G_PREMIUM", 35 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, age(GENERAL), true);

        assertThat(outcome.content()).contains("최근 평균 사용량은 데이터 35GB, 통화 100분, 문자 50건이에요",
                "5G 스탠다드(월 65,000원, 데이터 50GB)으로 바꾸면", "월 20,000원 아낄 수 있어요", "지금 5G 프리미엄은 월 85,000원이에요");
        assertThat(outcome.followUps()).containsExactly("요금제 변경은 어떻게 해요?");
    }

    @Test
    void 약정_중이면_할인을_적용한_금액으로_절약액을_알려준다() {
        subscribeAndUse("FIVE_G_PREMIUM", 35 * GB, 100, 50, true); // 85,000원 - 25% = 63,750원

        Outcome outcome = answerer.recommend(userId, age(GENERAL), true);

        // 5G 스탠다드 65,000원 - 25% = 48,750원이라 차이는 정가 기준 20,000원이 아니라 15,000원
        assertThat(outcome.content()).contains("월 15,000원 아낄 수 있어요", "지금 5G 프리미엄은 월 63,750원이에요",
                "월 금액은 모두 약정 할인 25%를 적용한 금액이에요").doesNotContain("월 20,000원 아낄");
        // 괄호 안 요약도 같은 기준의 할인가라서 정가(65,000원)와 섞이지 않는다
        assertThat(outcome.content()).contains("5G 스탠다드(월 48,750원, 데이터 50GB)으로 바꾸면")
                .doesNotContain("월 65,000원");
    }

    @Test
    void 약정_중에_그대로_쓰라고_할_때도_현재_요금제를_할인가로_보여준다() {
        subscribeAndUse("FIVE_G_STANDARD", 35 * GB, 100, 50, true);

        String content = answerer.recommend(userId, age(GENERAL), true).content();

        assertThat(content).contains("지금 쓰는 5G 스탠다드(월 48,750원, 데이터 50GB)이 사용량에 알맞아요",
                "월 금액은 모두 약정 할인 25%를 적용한 금액이에요").doesNotContain("월 65,000원");
    }

    @Test
    void 약정_중에_더_큰_요금제를_권할_때도_할인을_적용한_차이를_알려준다() {
        subscribeAndUse("FIVE_G_LITE", 20 * GB, 100, 50, true);

        Outcome outcome = answerer.recommend(userId, age(GENERAL), true);

        // 5G 라이트 45,000원 → 5G 스탠다드 65,000원은 정가 차이 20,000원이고 약정 할인 후에는 15,000원
        assertThat(outcome.content()).contains("월 15,000원이 더 들어요", "월 금액은 모두 약정 할인 25%를 적용한 금액이에요",
                "지금 쓰는 5G 라이트(월 33,750원, 데이터 8GB)", "5G 스탠다드(월 48,750원, 데이터 50GB)을 추천해요")
                .doesNotContain("월 20,000원이 더", "월 65,000원", "월 45,000원");
    }

    @Test
    void 약정이_만료됐으면_할인_없이_정가로_절약액을_알려준다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_PREMIUM", true);
        at(Instant.parse("2028-02-12T03:00:00Z")); // 약정 24개월이 지남
        setUsage(35 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, age(GENERAL), true);

        assertThat(outcome.content()).contains("월 20,000원 아낄 수 있어요", "지금 5G 프리미엄은 월 85,000원이에요")
                .doesNotContain("약정 할인");
    }

    @Test
    void 지금_요금제가_사용량에_알맞으면_그대로_쓰라고_한다() {
        subscribeAndUse("FIVE_G_STANDARD", 35 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, age(GENERAL), true);

        assertThat(outcome.content()).contains("지금 쓰는 5G 스탠다드(월 65,000원, 데이터 50GB)이 사용량에 알맞아요", "그대로 쓰시는 걸 추천해요");
        assertThat(outcome.followUps()).containsExactly("요금제 종류 알려줘");
    }

    @Test
    void 한도가_모자라면_어떤_항목이_모자란지_알리고_더_큰_요금제를_추천한다() {
        subscribeAndUse("FIVE_G_LITE", 20 * GB, 100, 50);

        String content = answerer.recommend(userId, age(GENERAL), true).content();

        assertThat(content).contains("지금 쓰는 5G 라이트(월 45,000원, 데이터 8GB)은 데이터가 부족해요",
                "5G 스탠다드(월 65,000원, 데이터 50GB)을 추천해요", "월 20,000원이 더 들어요");
    }

    @Test
    void 통화_한도가_모자라면_통화가_부족하다고_알린다() {
        subscribeAndUse("BUDGET_SAVE", 1 * GB, 600, 10);

        String content = answerer.recommend(userId, age(GENERAL), true).content();

        assertThat(content).contains("지금 쓰는 알뜰 세이브", "통화가 부족해요", "5G 라이트(월 45,000원");
    }

    @Test
    void 나이에_따라_추천이_달라지고_생년월일도_없으면_나이대를_되묻는다() {
        subscribeAndUse("FIVE_G_STANDARD", 20 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, Map.of(), true);

        assertThat(outcome.content()).isNull();
        assertThat(outcome.asks()).hasSize(1);
        assertThat(outcome.asks().getFirst().key()).isEqualTo("age_group");
        assertThat(outcome.asks().getFirst().question()).contains("나이대가 어떻게 되세요");
        assertThat(outcome.asks().getFirst().options()).containsExactly("만 14~18세", "만 19~64세", "만 65세 이상");
    }

    @Test
    void 되묻지_못하는_경로에서는_일반_요금제로만_추천한다() {
        subscribeAndUse("FIVE_G_STANDARD", 20 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, Map.of(), false);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("지금 쓰는 5G 스탠다드").contains("그대로 쓰시는 걸 추천해요");
    }

    @Test
    void 나이에_따라_추천이_달라지지_않으면_되묻지_않는다() {
        subscribeAndUse("FIVE_G_PREMIUM", 100 * GB, 100, 50);

        Outcome outcome = answerer.recommend(userId, Map.of(), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("지금 쓰는 5G 프리미엄");
    }

    @Test
    void 되묻기에_답한_나이대의_요금제까지_넣어_추천한다() {
        subscribeAndUse("FIVE_G_STANDARD", 20 * GB, 100, 50);

        Outcome youth = answerer.recommend(userId, age("만 14~18세"), false);
        Outcome general = answerer.recommend(userId, age("만 19~64세"), false);

        assertThat(youth.content()).contains("5G 청소년(월 55,000원, 데이터 80GB)으로 바꾸면", "월 10,000원 아낄 수 있어요");
        assertThat(general.content()).contains("지금 쓰는 5G 스탠다드").doesNotContain("청소년");
    }

    @Test
    void 시니어_요금제는_65세_이상이라고_답했을_때만_권한다() {
        subscribeAndUse("FIVE_G_LITE", 5 * GB, 100, 50);

        assertThat(answerer.recommend(userId, age("만 65세 이상"), false).content())
                .contains("LTE 시니어(월 29,000원, 데이터 8GB)으로 바꾸면", "월 16,000원 아낄 수 있어요");
        assertThat(answerer.recommend(userId, age("만 19~64세"), false).content()).doesNotContain("시니어");
    }

    @Test
    void 생년월일이_저장돼_있으면_되묻지_않고_그_나이대로_추천한다() {
        subscribeAndUse("FIVE_G_STANDARD", 20 * GB, 100, 50);
        jdbc.update("UPDATE users SET birth_date = '2010-05-01' WHERE user_id = ?", userId); // 만 16세
        entityManager.clear();

        Outcome outcome = answerer.recommend(userId, Map.of(), true);

        assertThat(outcome.asks()).isNull();
        assertThat(outcome.content()).contains("5G 청소년(월 55,000원");

        jdbc.update("UPDATE users SET birth_date = '1990-05-01' WHERE user_id = ?", userId);
        entityManager.clear();
        assertThat(answerer.recommend(userId, Map.of(), true).asks()).isNull();
        assertThat(answerer.recommend(userId, Map.of(), true).content()).doesNotContain("청소년");
    }

    @Test
    void 요금제를_바꾼_지_석_달이_안_됐으면_더_싼_요금제는_권하지_않는다() {
        subscribeAt(JANUARY_JOIN, "FIVE_G_STANDARD");
        at(Instant.parse("2026-09-10T03:00:00Z"));
        commandService.changePlan(userId, planId("FIVE_G_PREMIUM"), null); // 9월 11일부터 적용
        at(OCT_12);
        setUsage(35 * GB, 100, 50);

        assertThat(answerer.recommend(userId, age(GENERAL), true).content()).contains("지금 쓰는 5G 프리미엄", "그대로 쓰시는 걸 추천해요");

        at(Instant.parse("2027-01-12T03:00:00Z")); // 변경 후 석 달이 지남
        setUsage(35 * GB, 100, 50);
        assertThat(answerer.recommend(userId, age(GENERAL), true).content()).contains("5G 스탠다드(월 65,000원, 데이터 50GB)으로 바꾸면");
    }

    private String answered(Long member, String age, String data) {
        Map<String, String> confirmed = new java.util.HashMap<>(age(age));
        confirmed.put("monthly_data_usage", data);
        return answerer.recommend(member, confirmed, false).content();
    }

    private static Map<String, String> age(String answer) {
        return Map.of("age_group", answer);
    }

    private void subscribeAndUse(String planCode, long dataMb, int voice, int sms) {
        subscribeAndUse(planCode, dataMb, voice, sms, false);
    }

    private void subscribeAndUse(String planCode, long dataMb, int voice, int sms, boolean contract) {
        subscribeAt(JANUARY_JOIN, planCode, contract);
        at(OCT_12);
        setUsage(dataMb, voice, sms);
    }

    private void subscribeAt(Instant joinedAt, String planCode) {
        subscribeAt(joinedAt, planCode, false);
    }

    private void subscribeAt(Instant joinedAt, String planCode, boolean contract) {
        at(joinedAt);
        commandService.subscribe(userId, planId(planCode), null, contract);
    }

    // 사용량 행을 만든 뒤 모든 달의 값을 같게 고정해 평균이 곧 이 값이 되게 한다
    private void setUsage(long dataMb, int voice, int sms) {
        usageService.getOverview(userId);
        jdbc.update("UPDATE subscription_usages SET used_data_mb = ?, used_voice_minutes = ?, used_sms_count = ? "
                + "WHERE subscription_id IN (SELECT subscription_id FROM user_subscriptions WHERE user_id = ?)",
                dataMb, voice, sms, userId);
        entityManager.clear();
    }

    private void at(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Long planId(String code) {
        return planRepository.findAll().stream()
                .filter(plan -> plan.getCode().equals(code)).findFirst().orElseThrow().getPlanId();
    }
}
