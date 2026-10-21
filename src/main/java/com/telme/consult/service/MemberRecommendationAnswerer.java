package com.telme.consult.service;

import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.PlanRecommender.Demand;
import com.telme.consult.service.PlanRecommender.Kind;
import com.telme.consult.service.PlanRecommender.Recommendation;
import com.telme.consult.service.PlanRecommender.UsageFit;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberRecommendationAnswerer {

    public static final String AGE_KEY = AgeReplyParser.CONDITION_KEY;
    public static final String DATA_KEY = DataUsageReplyParser.CONDITION_KEY;
    public static final String VOICE_KEY = CallTextReplyParser.VOICE_KEY;
    public static final String SMS_KEY = CallTextReplyParser.SMS_KEY;
// 더 싼 요금제를 찾는 질문. 되묻기 조건이 아니라 질문에서 읽은 값이며 CHEAPER는 추천 사용량 기준, CHEAPEST는 사용량과 상관없이 싼 순서
public static final String PRICE_KEY = "price_request";
public static final String CHEAPER = "cheaper";
public static final String CHEAPEST = "cheapest";
    // 데이터 사용량을 받은 뒤에야 물을지 알 수 있는 질문. 앞선 되묻기를 마친 뒤에도 처음 묻는 것은 허용한다
    public static final java.util.Set<String> USAGE_FOLLOW_UP_KEYS = java.util.Set.of(VOICE_KEY, SMS_KEY);

    // content와 asks 중 하나만 있다. asks가 있으면 그 순서대로 되물어야 한다
    public record Outcome(String content, List<String> followUps, List<MissingCondition> asks) {
    }

    private static final String CATALOG_QUESTION = "요금제 종류 알려줘";
    private static final String CHANGE_QUESTION = "요금제 변경은 어떻게 해요?";
    private static final int COOLDOWN_MONTHS = 3;
    private static final int MB_PER_GB = 1024;
    private static final int MAX_CHEAPER = 3;
    // 말한 사용량이 요금제 데이터의 이 비율을 넘으면 한도에 가깝다고 알린다
    private static final double TIGHT_RATIO = 0.75;
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
    public Outcome recommend(Long userId, Map<String, String> confirmed, Map<String, String> remembered, boolean mayAsk) {
        Map<String, String> previous = withoutUnsure(remembered);
        Map<String, String> answers = new HashMap<>(previous);
        answers.putAll(confirmed);
        boolean ageReused = !confirmed.containsKey(AGE_KEY) && previous.containsKey(AGE_KEY);
        boolean dataReused = !confirmed.containsKey(DATA_KEY) && previous.containsKey(DATA_KEY);
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
        boolean subscribed = subscription != null;
        Long subscribedPlanId = subscription == null || subscription.currentPlan() == null
                ? null : subscription.currentPlan().planId();
        AgeGroup age = ageGroup(userId, ageAnswer);
        Plan.TargetGroup group = age.group();
        List<Plan> active = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
        if (usage.isEmpty()) {
            return byAnswers(group, answers, active, userId != null, mayAsk,
                    reuseNote(ageReused && age.fromAnswer(), dataReused), answers.get(PRICE_KEY),
                    new Subscriber(subscribed, subscribedPlanId));
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
            return answer(general, current, demand, usage.get(), discountPercent(subscription), note);
        }
        return answer(recommender.recommend(pool(active, group), current, demand, allowDowngrade), current, demand,
                usage.get(), discountPercent(subscription), note);
    }

    // 나이대 질문의 선택지 글자. 직접 말한 나이도 이 글자로 맞춰 쓴다
    static String ageOption(Plan.TargetGroup group) {
        return switch (group) {
            case YOUTH -> YOUTH_OPTION;
            case SENIOR -> SENIOR_OPTION;
            default -> GENERAL_OPTION;
        };
    }

    // 앞서 "모르겠어요"라고 한 사용량은 다시 쓰지 않는다. 그대로 쓰면 다음 추천이 되묻지 않고 안내로만 끝난다
    private static Map<String, String> withoutUnsure(Map<String, String> remembered) {
        Map<String, String> reusable = new HashMap<>(remembered);
        reusable.entrySet().removeIf(entry -> isUnsure(entry.getKey(), entry.getValue()));
        return reusable;
    }

    private static boolean isUnsure(String key, String value) {
        return switch (key) {
            case DATA_KEY -> DataUsageReplyParser.parse(value).kind() == DataUsageReplyParser.Kind.UNSURE;
            case VOICE_KEY -> CallTextReplyParser.parseVoice(value).kind() == CallTextReplyParser.Kind.UNSURE;
            case SMS_KEY -> CallTextReplyParser.parseSms(value).kind() == CallTextReplyParser.Kind.UNSURE;
            default -> false;
        };
    }

    // 사용량이 없으면 나이대와 한 달 데이터 사용량을 묻고, 말한 사용량에 여유를 둔 요금제를 추천한다.
    // 고른 요금제에 통화·문자 한도가 있으면 그 항목도 묻는다
    private Outcome byAnswers(Plan.TargetGroup group, Map<String, String> answers, List<Plan> active,
            boolean member, boolean mayAsk, String note, String priceRequest, Subscriber subscriber) {
        DataUsageReplyParser.Result usage = DataUsageReplyParser.parse(answers.get(DATA_KEY));
        if (usage.kind() == DataUsageReplyParser.Kind.UNSURE) {
            return nudge("사용량을 잘 모르시면 가입하신 뒤 사용량이 쌓였을 때 맞춰 추천해 드릴 수 있어요.");
        }
        boolean answered = usage.readable();
        if (mayAsk && (group == null || !answered)) {
            List<MissingCondition> asks = new ArrayList<>();
            if (group == null) {
                asks.add(ageQuestion());
            }
            if (!answered) {
                asks.add(dataQuestion());
            }
            return new Outcome(null, List.of(), asks);
        }
        if (!answered) {
            return nudge("사용량을 알려주시면 맞는 요금제를 추천해 드릴 수 있어요.");
        }
        CallTextReplyParser.Result voice = CallTextReplyParser.parseVoice(answers.get(VOICE_KEY));
        CallTextReplyParser.Result sms = CallTextReplyParser.parseSms(answers.get(SMS_KEY));
        boolean unlimited = usage.kind() == DataUsageReplyParser.Kind.UNLIMITED;
        Demand demand = new Demand(unlimited ? Long.MAX_VALUE : usage.dataMb(), amountOf(voice), amountOf(sms));
        UsageFit fit = recommender.forUsage(pool(active, group), demand);
        if (fit == null) {
            return nudge("지금은 추천할 수 있는 요금제가 없어요.");
        }
        UsageLimits limits = UsageLimits.of(fit.cheapest());
        List<MissingCondition> asks = new ArrayList<>();
        if (limits.voiceMinutes() != null && !voice.readable()) {
            asks.add(voiceQuestion());
        }
        if (limits.smsCount() != null && !sms.readable()) {
            asks.add(smsQuestion());
        }
        if (mayAsk && !asks.isEmpty()) {
            return new Outcome(null, List.of(), asks);
        }
        if (priceRequest != null) {
            return new Outcome(cheaperPlans(usage, fit, pool(active, group), CHEAPEST.equals(priceRequest)) + note,
                    List.of(CATALOG_QUESTION), null);
        }
        String tail = member ? " 가입한 요금제의 사용량이 쌓이면 더 정확히 추천해 드려요."
                : " 로그인하고 요금제에 가입하시면 평소 사용량에 맞춰 더 정확히 추천해 드려요.";
        return new Outcome(recommendation(usage, fit) + limitNotice(limits, voice, sms) + tail + note,
                withLink(List.of(CATALOG_QUESTION), subscriber.link(fit.cheapest())), null);
    }

    // 가입 중인 회원이면 변경, 아니면 가입 화면으로 가는 추천 요금제 링크. 지금 쓰는 요금제를 추천했으면 링크가 없다
    private record Subscriber(boolean subscribed, Long planId) {
        String link(Plan recommended) {
            if (!subscribed) {
                return PlanTaskLinks.subscribeTo(recommended.getName());
            }
            return recommended.getPlanId().equals(planId) ? null : PlanTaskLinks.changeTo(recommended.getName());
        }
    }

    private static List<String> withLink(List<String> followUps, String link) {
        if (link == null) {
            return followUps;
        }
        List<String> withLink = new ArrayList<>(followUps);
        withLink.add(link);
        return withLink;
    }

    // 추천한 요금제보다 싼 요금제를 알린다. 사용량 기준이면 데이터가 많은 순, 사용량과 상관없이면 싼 순서로 3개까지이고,
    // 추천 요금제가 이미 가장 싸면 그렇게 알린다. 싼 요금제는 사용량을 못 채울 수 있다는 말을 붙인다
    private static String cheaperPlans(DataUsageReplyParser.Result usage, UsageFit fit, List<Plan> pool, boolean regardless) {
        Plan base = fit.cheapest();
        String basis = usage.kind() == DataUsageReplyParser.Kind.UNLIMITED ? "데이터를 무제한으로 쓰신다면"
                : "한 달에 데이터를 %s 쓰신다면".formatted(gb(usage.dataMb()));
        Comparator<Plan> order = regardless ? Comparator.comparingInt(Plan::getMonthlyFee)
                : Comparator.comparingDouble((Plan plan) -> dataMb(plan)).reversed().thenComparingInt(Plan::getMonthlyFee);
        List<Plan> cheaper = pool.stream().filter(plan -> plan.getMonthlyFee() < base.getMonthlyFee())
                .sorted(order).limit(MAX_CHEAPER).toList();
        if (cheaper.isEmpty()) {
            return "%s %s(%s)이 가장 저렴한 요금제예요. 이보다 싼 요금제는 없어요.".formatted(basis, base.getName(), summary(base));
        }
        String list = cheaper.stream().map(plan -> "%s(%s)".formatted(plan.getName(), summary(plan)))
                .collect(Collectors.joining(", "));
        if (regardless) {
            return "사용량과 상관없이 싼 순서로는 %s이에요. 한도가 작아서 사용량을 채우지 못할 수 있어요. %s %s(%s)이 사용량에 맞는 가장 저렴한 요금제예요."
                    .formatted(list, basis, base.getName(), summary(base));
        }
        return "%s %s(%s)이 사용량에 맞는 가장 저렴한 요금제예요. 더 싼 요금제로는 %s이 있는데, 사용량을 채우지 못하고 모자랄 수 있어요."
                .formatted(basis, base.getName(), summary(base), list);
    }

    private static double dataMb(Plan plan) {
        Long limit = UsageLimits.of(plan).dataMb();
        return limit == null ? Double.MAX_VALUE : limit;
    }

    // 모르거나 말하지 않은 항목은 0으로 두고, 무제한이라고 한 항목은 한도가 없는 요금제만 채운다
    private static long amountOf(CallTextReplyParser.Result result) {
        return switch (result.kind()) {
            case AMOUNT -> result.value();
            case UNLIMITED -> Long.MAX_VALUE;
            default -> 0;
        };
    }

    // 한도가 있는 항목을 모르는 채로 추천했으면 한도를 알려 확인하게 한다
    private static String limitNotice(UsageLimits limits, CallTextReplyParser.Result voice, CallTextReplyParser.Result sms) {
        List<String> unknown = new ArrayList<>();
        if (limits.voiceMinutes() != null && notKnown(voice)) {
            unknown.add("통화 %d분".formatted(limits.voiceMinutes()));
        }
        if (limits.smsCount() != null && notKnown(sms)) {
            unknown.add("문자 %d건".formatted(limits.smsCount()));
        }
        return unknown.isEmpty() ? "" : " 이 요금제는 %s까지 쓸 수 있으니 통화·문자를 많이 쓰신다면 확인해 보세요."
                .formatted(String.join(", ", unknown));
    }

    private static boolean notKnown(CallTextReplyParser.Result result) {
        return result.kind() == CallTextReplyParser.Kind.UNSURE || result.kind() == CallTextReplyParser.Kind.UNREADABLE;
    }

    private static String recommendation(DataUsageReplyParser.Result usage, UsageFit fit) {
        Plan plan = fit.cheapest();
        StringBuilder text = new StringBuilder(usage.kind() == DataUsageReplyParser.Kind.UNLIMITED
                ? "데이터를 무제한으로 쓰신다면 %s(%s) 요금제를 추천해요.".formatted(plan.getName(), summary(plan))
                : "한 달에 데이터를 %s 쓰신다면 %s(%s) 요금제를 추천해요.".formatted(gb(usage.dataMb()), plan.getName(), summary(plan)));
        if (isTight(plan, usage)) {
            text.append(" 한도에 가까워서 사용량이 늘면 모자랄 수 있어요.");
        }
        if (fit.fiveG() != null && plan.getNetworkType() == Plan.NetworkType.LTE) {
            text.append(" 5G로 쓰시려면 %s(%s) 요금제가 가장 저렴해요.".formatted(fit.fiveG().getName(), summary(fit.fiveG())));
        }
        return text.toString();
    }

    private static boolean isTight(Plan plan, DataUsageReplyParser.Result usage) {
        Long limitMb = UsageLimits.of(plan).dataMb();
        return usage.kind() == DataUsageReplyParser.Kind.AMOUNT && limitMb != null && usage.dataMb() >= limitMb * TIGHT_RATIO;
    }

    // 일반 요금제는 누구나, 청소년·시니어 요금제는 해당하는 나이대만 고를 수 있다
    private static List<Plan> pool(List<Plan> active, Plan.TargetGroup group) {
        return active.stream()
                .filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.GENERAL || plan.getTargetGroup() == group)
                .toList();
    }

    private record AgeGroup(Plan.TargetGroup group, boolean fromAnswer) {
    }

    // 생년월일이 있으면 그것을, 없으면 되묻기 답을 쓴다(선택지 글자 또는 직접 입력한 나이). 둘 다 없으면 group이 null
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
        if (GENERAL_OPTION.equals(ageAnswer)) {
            return new AgeGroup(Plan.TargetGroup.GENERAL, true);
        }
        Plan.TargetGroup typed = AgeReplyParser.parse(ageAnswer);
        return typed == null ? new AgeGroup(null, false) : new AgeGroup(typed, true);
    }

    // 앞서 한 답을 다시 묻지 않고 썼으면 답변 끝에 알린다
    private static String reuseNote(boolean age, boolean data) {
        if (!age && !data) {
            return "";
        }
        String what = age && data ? "나이대와 데이터 사용량" : age ? "나이대" : "데이터 사용량";
        return " 앞서 알려주신 %s 답을 기준으로 했어요.".formatted(what);
    }

    private static MissingCondition ageQuestion() {
        return new MissingCondition(AGE_KEY, "나이에 따라 추천하는 요금제가 달라져요. 나이대가 어떻게 되세요?",
                List.of(YOUTH_OPTION, GENERAL_OPTION, SENIOR_OPTION), null);
    }

    private static MissingCondition voiceQuestion() {
        return new MissingCondition(VOICE_KEY, "한 달에 통화는 대략 몇 분 쓰세요? 잘 모르시면 모른다고 해 주세요.", List.of(), null);
    }

    private static MissingCondition smsQuestion() {
        return new MissingCondition(SMS_KEY, "한 달에 문자는 대략 몇 건 보내세요? 잘 모르시면 모른다고 해 주세요.", List.of(), null);
    }

    private static MissingCondition dataQuestion() {
        return new MissingCondition(DATA_KEY, "한 달에 데이터를 얼마나 쓰세요? 대략 몇 GB인지 채팅으로 알려주세요. 잘 모르시면 모른다고 해 주세요.", List.of(), null);
    }

    // 요금제를 바꾼 지 얼마 안 됐으면 곧바로 다시 바꾸라고 권하지 않는다
    private boolean changedRecently(Long subscriptionId) {
        var now = calendar.now();
        return historyRepository.findBySubscriptionIdOrderByEffectiveFromAscHistoryIdAsc(subscriptionId).stream()
                .anyMatch(history -> history.getType() == SubscriptionHistory.Type.CHANGE
                        && calendar.plusMonths(history.getEffectiveFrom(), COOLDOWN_MONTHS).isAfter(now));
    }

    // 약정 중이면 요금제를 바꿔도 같은 할인율이 붙으므로 비교하는 두 요금제 모두 할인 후 금액으로 본다
    private Outcome answer(Recommendation recommendation, Plan current, Demand demand, UsageDemand usage,
            int discountPercent, String note) {
        Plan plan = recommendation.plan();
        String use = "최근 평균 사용량은 데이터 %s, 통화 %d분, 문자 %d건이에요.".formatted(
                gb(usage.dataMb()), usage.voiceMinutes(), usage.smsCount());
        int currentFee = afterDiscount(current.getMonthlyFee(), discountPercent);
        int gap = Math.abs(afterDiscount(plan.getMonthlyFee(), discountPercent) - currentFee);
        Outcome outcome = switch (recommendation.kind()) {
            case KEEP -> new Outcome("%s 지금 쓰는 %s(%s)이 사용량에 알맞아요. 그대로 쓰시는 걸 추천해요.%s"
                    .formatted(use, current.getName(), summary(current, discountPercent), discountNote(discountPercent)),
                    List.of(CATALOG_QUESTION), null);
            case CHEAPER -> new Outcome("%s %s(%s)으로 바꾸면 사용량을 채우면서 월 %s 아낄 수 있어요. 지금 %s은 월 %s이에요.%s"
                    .formatted(use, plan.getName(), summary(plan, discountPercent), PlanFormat.money(gap),
                            current.getName(), PlanFormat.money(currentFee), discountNote(discountPercent)),
                    List.of(CHANGE_QUESTION, PlanTaskLinks.changeTo(plan.getName())), null);
            case UPGRADE -> new Outcome("%s 지금 쓰는 %s(%s)은 %s 부족해요. %s(%s)을 추천해요. 월 %s이 더 들어요.%s"
                    .formatted(use, current.getName(), summary(current, discountPercent), shortage(current, demand),
                            plan.getName(), summary(plan, discountPercent), PlanFormat.money(gap),
                            discountNote(discountPercent)),
                    List.of(CHANGE_QUESTION, PlanTaskLinks.changeTo(plan.getName())), null);
        };
        return note.isEmpty() ? outcome : new Outcome(outcome.content() + note, outcome.followUps(), null);
    }

    private static int discountPercent(SubscriptionResponse subscription) {
        var contract = subscription.contract();
        return contract != null && contract.active() ? contract.discountPercent() : 0;
    }

    // 예상 요금 계산(SubscriptionUsageService.feeAfterContract)과 같은 식
    private static int afterDiscount(int monthlyFee, int discountPercent) {
        return monthlyFee - monthlyFee * discountPercent / 100;
    }

    private static String discountNote(int discountPercent) {
        return discountPercent == 0 ? "" : " 월 금액은 모두 약정 할인 %d%%를 적용한 금액이에요.".formatted(discountPercent);
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
        return summary(plan, 0);
    }

    // 약정 중이면 비교하는 금액과 같은 기준으로 할인 후 월정액을 보여준다
    private static String summary(Plan plan, int discountPercent) {
        return "월 %s, 데이터 %s".formatted(PlanFormat.money(afterDiscount(plan.getMonthlyFee(), discountPercent)),
                PlanFormat.data(plan.isUnlimited(), plan.getDataGb()));
    }

    private static String gb(long mb) {
        if (mb < MB_PER_GB) {
            return mb + "MB";
        }
        return new BigDecimal(mb).divide(BigDecimal.valueOf(MB_PER_GB), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "GB";
    }

    private static Outcome nudge(String head) {
        return new Outcome(head + " 요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.", List.of(CATALOG_QUESTION), null);
    }
}
