package com.telme.consult.service;

import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.PlanRecommender.Demand;
import com.telme.consult.service.PlanRecommender.Kind;
import com.telme.consult.service.PlanRecommender.Recommendation;
import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.entity.SubscriptionHistory;
import com.telme.subscription.repository.SubscriptionHistoryRepository;
import com.telme.subscription.service.SubscriptionCalendar;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.usage.service.SubscriptionUsageService;
import com.telme.usage.service.UsageDemand;
import com.telme.usage.service.UsageLimits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberRecommendationAnswerer {

    public static final String AGE_KEY = "age_group";
    public static final String HEAVY_KEY = "heavy_data";

    // content와 asks 중 하나만 있다. asks가 있으면 그 순서대로 되물어야 한다
    public record Outcome(String content, List<String> followUps, List<MissingCondition> asks) {
    }

    private static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    private static final String CHANGE_QUESTION = "요금제 변경은 어떻게 해요?";
    private static final int COOLDOWN_MONTHS = 3;
    private static final int MB_PER_GB = 1024;
    private static final long HEAVY_DATA_MB = 50L * MB_PER_GB;
    private static final long LIGHT_DATA_MB = 3L * MB_PER_GB;
    private static final String YES = "예";
    private static final String NO = "아니요";
    private static final String YOUTH_OPTION = "만 %d~%d세".formatted(
            SubscriptionEligibilityChecker.YOUTH_MIN_AGE, SubscriptionEligibilityChecker.YOUTH_MAX_AGE);
    private static final String GENERAL_OPTION = "만 %d~%d세".formatted(
            SubscriptionEligibilityChecker.YOUTH_MAX_AGE + 1, SubscriptionEligibilityChecker.SENIOR_MIN_AGE - 1);
    private static final String SENIOR_OPTION = "만 %d세 이상".formatted(SubscriptionEligibilityChecker.SENIOR_MIN_AGE);

    private final SubscriptionQueryService queryService;
    private final SubscriptionUsageService usageService;
    private final SubscriptionHistoryRepository historyRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionCalendar calendar;
    private final PlanRecommender recommender = new PlanRecommender();

    public Outcome recommend(Long userId, Map<String, String> confirmed, boolean mayAsk) {
        return recommend(userId, confirmed, Map.of(), mayAsk);
    }

    // 회원이 아니면 userId는 null. confirmed는 이번 질문의 되묻기 답, previous는 같은 채팅방에서 앞서 한 답이고
    // 앞서 한 답은 다시 묻지 않고 쓴다. mayAsk가 false이면 되묻지 않는다
    public Outcome recommend(Long userId, Map<String, String> confirmed, Map<String, String> previous, boolean mayAsk) {
        Map<String, String> answers = new HashMap<>(previous);
        answers.putAll(confirmed);
        boolean ageReused = !confirmed.containsKey(AGE_KEY) && previous.containsKey(AGE_KEY);
        boolean heavyReused = !confirmed.containsKey(HEAVY_KEY) && previous.containsKey(HEAVY_KEY);
        String ageAnswer = answers.get(AGE_KEY);
        Optional<UsageDemand> usage = Optional.empty();
        Plan current = null;
        SubscriptionResponse subscription = null;
        if (userId != null) {
            SubscriptionMeResponse mine = queryService.getMine(userId);
            if (mine.subscribed()) {
                subscription = mine.subscription();
                usage = subscription.currentPlan() == null ? Optional.empty() : usageService.getDemand(userId);
                if (usage.isPresent()) {
                    current = planRepository.findById(subscription.currentPlan().planId()).orElseThrow();
                }
            }
        }
        AgeGroup age = ageGroup(userId, ageAnswer);
        Plan.TargetGroup group = age.group();
        List<Plan> active = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
        if (usage.isEmpty()) {
            return byAnswers(group, answers.get(HEAVY_KEY), active, userId != null, mayAsk,
                    reuseNote(ageReused && age.fromAnswer(), heavyReused));
        }
        String note = reuseNote(ageReused && age.fromAnswer(), false);
        Demand demand = new Demand(usage.get().dataMb(), usage.get().voiceMinutes(), usage.get().smsCount());
        boolean allowDowngrade = !changedRecently(subscription.subscriptionId());
        if (group == null) {
            Recommendation general = recommender.recommend(pool(active, null), current, demand, allowDowngrade);
            Recommendation any = recommender.recommend(active, current, demand, allowDowngrade);
            if (mayAsk && !any.plan().equals(general.plan())) {
                return new Outcome(null, List.of(), List.of(ageQuestion()));
            }
            return answer(general, current, demand, usage.get(), note);
        }
        return answer(recommender.recommend(pool(active, group), current, demand, allowDowngrade), current, demand,
                usage.get(), note);
    }

    // 사용량이 없으면 나이대와 데이터를 많이 쓰는지만 묻고 그에 맞는 요금제를 추천한다
    private Outcome byAnswers(Plan.TargetGroup group, String heavy, List<Plan> active,
            boolean member, boolean mayAsk, String note) {
        boolean answered = YES.equals(heavy) || NO.equals(heavy);
        if (mayAsk && (group == null || !answered)) {
            List<MissingCondition> asks = new ArrayList<>();
            if (group == null) {
                asks.add(ageQuestion());
            }
            if (!answered) {
                asks.add(heavyQuestion());
            }
            return new Outcome(null, List.of(), asks);
        }
        if (!answered) {
            return nudge("사용량을 알려주시면 맞는 요금제를 추천해 드릴 수 있어요.");
        }
        boolean isHeavy = YES.equals(heavy);
        Plan plan = recommender.forDataNeed(pool(active, group), isHeavy ? HEAVY_DATA_MB : LIGHT_DATA_MB);
        if (plan == null) {
            return nudge("지금은 추천할 수 있는 요금제가 없어요.");
        }
        String tail = member ? " 가입한 요금제의 사용량이 쌓이면 더 정확히 추천해 드려요."
                : " 로그인하고 요금제에 가입하시면 평소 사용량에 맞춰 더 정확히 추천해 드려요.";
        return new Outcome("데이터를 %s %s(%s) 요금제를 추천해요.%s".formatted(
                isHeavy ? "많이 쓰신다면" : "적게 쓰신다면", plan.getName(), summary(plan), tail) + note,
                List.of(CATALOG_QUESTION), null);
    }

    // 일반 요금제는 누구나, 청소년·시니어 요금제는 해당하는 나이대만 고를 수 있다
    private static List<Plan> pool(List<Plan> active, Plan.TargetGroup group) {
        return active.stream()
                .filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.GENERAL || plan.getTargetGroup() == group)
                .toList();
    }

    private record AgeGroup(Plan.TargetGroup group, boolean fromAnswer) {
    }

    // 생년월일이 있으면 그것을, 없으면 되묻기 답을 쓴다. 둘 다 없으면 group이 null
    private AgeGroup ageGroup(Long userId, String ageAnswer) {
        LocalDate birthDate = userId == null ? null : userRepository.findById(userId).map(User::getBirthDate).orElse(null);
        if (birthDate != null) {
            int age = Period.between(birthDate, calendar.today(calendar.now())).getYears();
            if (age >= SubscriptionEligibilityChecker.SENIOR_MIN_AGE) {
                return new AgeGroup(Plan.TargetGroup.SENIOR, false);
            }
            return new AgeGroup(age >= SubscriptionEligibilityChecker.YOUTH_MIN_AGE && age <= SubscriptionEligibilityChecker.YOUTH_MAX_AGE
                    ? Plan.TargetGroup.YOUTH : Plan.TargetGroup.GENERAL, false);
        }
        if (YOUTH_OPTION.equals(ageAnswer)) {
            return new AgeGroup(Plan.TargetGroup.YOUTH, true);
        }
        if (SENIOR_OPTION.equals(ageAnswer)) {
            return new AgeGroup(Plan.TargetGroup.SENIOR, true);
        }
        return GENERAL_OPTION.equals(ageAnswer) ? new AgeGroup(Plan.TargetGroup.GENERAL, true) : new AgeGroup(null, false);
    }

    // 앞서 한 답을 다시 묻지 않고 썼으면 답변 끝에 알린다
    private static String reuseNote(boolean age, boolean heavy) {
        if (!age && !heavy) {
            return "";
        }
        String what = age && heavy ? "나이대와 데이터 사용량" : age ? "나이대" : "데이터 사용량";
        return " 앞서 알려주신 %s 답을 기준으로 했어요.".formatted(what);
    }

    private static MissingCondition ageQuestion() {
        return new MissingCondition(AGE_KEY, "나이에 따라 추천하는 요금제가 달라져요. 나이대가 어떻게 되세요?",
                List.of(YOUTH_OPTION, GENERAL_OPTION, SENIOR_OPTION), null);
    }

    private static MissingCondition heavyQuestion() {
        return new MissingCondition(HEAVY_KEY, "데이터를 많이 쓰시는 편인가요? (한 달 50GB 이상)", List.of(YES, NO), null);
    }

    // 요금제를 바꾼 지 얼마 안 됐으면 곧바로 다시 바꾸라고 권하지 않는다
    private boolean changedRecently(Long subscriptionId) {
        var now = calendar.now();
        return historyRepository.findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(subscriptionId).stream()
                .anyMatch(history -> history.getType() == SubscriptionHistory.Type.CHANGE
                        && calendar.plusMonths(history.getEffectiveFrom(), COOLDOWN_MONTHS).isAfter(now));
    }

    private Outcome answer(Recommendation recommendation, Plan current, Demand demand, UsageDemand usage, String note) {
        Plan plan = recommendation.plan();
        String use = "최근 평균 사용량은 데이터 %s, 통화 %d분, 문자 %d건이에요.".formatted(
                gb(usage.dataMb()), usage.voiceMinutes(), usage.smsCount());
        int gap = Math.abs(plan.getMonthlyFee() - current.getMonthlyFee());
        Outcome outcome = switch (recommendation.kind()) {
            case KEEP -> new Outcome("%s 지금 쓰는 %s(%s)이 사용량에 알맞아요. 그대로 쓰시는 걸 추천해요."
                    .formatted(use, current.getName(), summary(current)), List.of(CATALOG_QUESTION), null);
            case CHEAPER -> new Outcome("%s %s(%s)으로 바꾸면 사용량을 채우면서 월 %s 아낄 수 있어요. 지금 %s은 월 %s이에요."
                    .formatted(use, plan.getName(), summary(plan), PlanFormat.money(gap), current.getName(),
                            PlanFormat.money(current.getMonthlyFee())), List.of(CHANGE_QUESTION), null);
            case UPGRADE -> new Outcome("%s 지금 쓰는 %s(%s)은 %s 부족해요. %s(%s)을 추천해요. 월 %s이 더 들어요."
                    .formatted(use, current.getName(), summary(current), shortage(current, demand), plan.getName(),
                            summary(plan), PlanFormat.money(gap)), List.of(CHANGE_QUESTION), null);
        };
        return note.isEmpty() ? outcome : new Outcome(outcome.content() + note, outcome.followUps(), null);
    }

    private static String shortage(Plan current, Demand demand) {
        var limits = UsageLimits.of(current);
        List<String> lacking = new ArrayList<>();
        if (limits.dataMb() != null && limits.dataMb() < demand.dataMb() * PlanRecommender.MARGIN) {
            lacking.add("데이터가");
        }
        if (limits.voiceMinutes() != null && limits.voiceMinutes() < demand.voiceMinutes() * PlanRecommender.MARGIN) {
            lacking.add("통화가");
        }
        if (limits.smsCount() != null && limits.smsCount() < demand.smsCount() * PlanRecommender.MARGIN) {
            lacking.add("문자가");
        }
        return lacking.isEmpty() ? "여유가 없어" : String.join(" ", lacking);
    }

    private static String summary(Plan plan) {
        return "월 %s, 데이터 %s".formatted(PlanFormat.money(plan.getMonthlyFee()), PlanFormat.data(plan.isUnlimited(), plan.getDataGb()));
    }

    private static String gb(long mb) {
        return new BigDecimal(mb).divide(BigDecimal.valueOf(MB_PER_GB), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "GB";
    }

    private static Outcome nudge(String head) {
        return new Outcome(head + " 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.", List.of(CATALOG_QUESTION), null);
    }
}
