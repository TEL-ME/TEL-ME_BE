package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanCatalogAnswerer {

    private static final Pattern CHEAPEST = Pattern.compile("제일싼|가장싼|제일저렴|가장저렴|제일싸|최저가");
    private static final Pattern PRICIEST = Pattern.compile("제일비싼|가장비싼|제일비싸|최고가");
    private static final Pattern COUNT = Pattern.compile("몇종류|몇가지|몇개|몇종");
    private static final Pattern PRICE_BAND = Pattern.compile("(?<!\\d)(\\d{1,4})만원대");
    private static final Pattern PRICE_UNDER = Pattern.compile("(?<!\\d)(\\d{1,4})만원(이하|안쪽|이내|미만)");
    private static final int WON_PER_MAN = 10_000;
    // 낱말 바로 뒤나 "청소년이나 시니어 말고"처럼 다른 대상을 하나 더 든 뒤에 오는 제외 표현(띄어쓰기를 뺀 문장 기준)
    private static final Pattern EXCLUDED_AFTER = Pattern.compile(
            "(?:요금제)?(?:은|는|이나|나|랑|이랑|과|와|,)?(?:[가-힣A-Z0-9]{1,4}(?:요금제)?)?(?:말고|빼고|제외|아닌|필요없)");

    private final PlanRepository planRepository;

    // 판매 중인 요금제가 없으면 null
    public String answer(String question) {
        return answer(question, null);
    }

    // mention은 분류 모델이 판매 중인 목록에서 고른 요금제. 없으면(규칙 분류) 질문에서 요금제 이름을 직접 찾는다
    public String answer(String question, PlanQuestion.Mention mention) {
        List<Plan> plans = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
        if (plans.isEmpty()) {
            return null;
        }
        if (mention != null && mention.unknownPlan()) {
            return "말씀하신 요금제는 판매 중인 요금제에 없어요. 판매 중인 전체 요금제는 아래와 같아요.\n\n" + list(plans);
        }
        // 영문 대소문자 구분 없이 망 이름(5G, LTE)을 찾는다
        String text = question.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        List<Plan> named = mention != null
                // 모델이 고른 요금제도 "프리미엄 빼고"처럼 뺀다고 한 것은 상세로 보여주지 않는다
                ? plans.stream().filter(plan -> mention.planCodes().contains(plan.getCode()))
                        .filter(plan -> !excludedByName(text, plan, plans)).toList()
                : namedPlans(text, plans);
        if (!named.isEmpty()) {
            return detail(named);
        }
        boolean cheapest = CHEAPEST.matcher(text).find();
        boolean priciest = PRICIEST.matcher(text).find();
        PlanQuestion.Conditions conditions = mention == null ? null : mention.conditions();
        List<Plan> matched = filter(text, plans, cheapest || priciest, conditions);
        // 대상을 물었는지도 모델 조건이 있으면 그것으로 본다. "엄마는 시니어 요금제 쓰는데"처럼 이유로 꺼낸 대상은 묻지 않은 것이다
        boolean groupAsked = conditions != null
                ? !conditions.targetGroups().isEmpty() || !conditions.excludedTargetGroups().isEmpty()
                : asksTargetGroup(text);
        if (cheapest || priciest) {
            return extreme(matched, cheapest, groupAsked);
        }
        if (matched.isEmpty()) {
            return "조건에 맞는 요금제를 찾지 못했어요. 판매 중인 전체 요금제는 아래와 같아요.\n\n" + list(plans);
        }
        String head = COUNT.matcher(text).find() ? count(matched, groupAsked) : "요금제 안내예요.";
        return head + "\n\n" + list(matched);
    }

    // 개수는 FAQ처럼 일반 라인업 기준으로 세고 청소년·시니어 요금제는 따로 알린다. 대상을 직접 물으면 그 대상을 센다
    private String count(List<Plan> matched, boolean groupAsked) {
        List<Plan> general = matched.stream().filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.GENERAL).toList();
        if (general.isEmpty() || general.size() == matched.size() || groupAsked) {
            return "판매 중인 요금제는 총 %d종이에요.".formatted(matched.size());
        }
        long youth = matched.stream().filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.YOUTH).count();
        long senior = matched.stream().filter(plan -> plan.getTargetGroup() == Plan.TargetGroup.SENIOR).count();
        List<String> others = new ArrayList<>();
        if (youth > 0) {
            others.add("청소년 %d종".formatted(youth));
        }
        if (senior > 0) {
            others.add("시니어 %d종".formatted(senior));
        }
        return "일반 %s요금제는 %d종이에요. %s은 따로 있어요.".formatted(networkPrefix(general), general.size(), String.join(", ", others));
    }

    private static boolean asksTargetGroup(String text) {
        return AgeReplyParser.YOUTH_WORD.matcher(text).find() || AgeReplyParser.SENIOR_WORD.matcher(text).find();
    }

    // 한 망만 물었으면 "일반 LTE 요금제"처럼 망 이름을 붙인다
    private static String networkPrefix(List<Plan> plans) {
        List<Plan.NetworkType> networks = plans.stream().map(Plan::getNetworkType).distinct().toList();
        return networks.size() == 1 ? networkLabel(networks.getFirst()) + " " : "";
    }

    private List<Plan> namedPlans(String text, List<Plan> plans) {
        List<Plan> named = new ArrayList<>();
        for (Plan plan : plans) {
            String name = plan.getName().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            String alias = alias(plan, plans);
            if ((text.contains(name) || alias != null && text.contains(alias.toUpperCase(Locale.ROOT)))
                    && !excludedByName(text, plan, plans)) {
                named.add(plan);
            }
        }
        return named;
    }

    private String withoutExcludedNames(String text, List<Plan> plans) {
        String result = text;
        for (Plan plan : plans) {
            if (!excludedByName(text, plan, plans)) {
                continue;
            }
            String alias = alias(plan, plans);
            result = result.replace(plan.getName().replaceAll("\\s+", "").toUpperCase(Locale.ROOT), "#");
            if (alias != null) {
                result = result.replace(alias.toUpperCase(Locale.ROOT), "#");
            }
        }
        return result;
    }

    // "언리미티드 말고", "LTE 맥스 제외하고"처럼 이름이나 망 이름을 뗀 이름 뒤에 제외 표현이 오면 그 요금제를 뺀다
    private boolean excludedByName(String text, Plan plan, List<Plan> plans) {
        String alias = alias(plan, plans);
        for (String word : alias == null ? List.of(plan.getName()) : List.of(plan.getName(), alias)) {
            String key = word.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            for (int index = text.indexOf(key); index >= 0; index = text.indexOf(key, index + 1)) {
                if (EXCLUDED_AFTER.matcher(text.substring(index + key.length())).lookingAt()) {
                    return true;
                }
            }
        }
        return false;
    }

    // 망 이름을 뗀 상품 이름(스탠다드 등). 다른 요금제와 겹치면(청소년·시니어) 쓰지 않는다
    private String alias(Plan plan, List<Plan> plans) {
        String[] parts = plan.getName().split("\\s+", 2);
        if (parts.length < 2) {
            return null;
        }
        long same = plans.stream().filter(other -> other.getName().endsWith(" " + parts[1])).count();
        return same == 1 ? parts[1] : null;
    }

    // 모델이 읽은 조건(망, 대상, 무제한, 뺄 요금제)이 있으면 그것을 쓰고, 없으면(규칙 분류, 요금제를 고른 질문, 조건 호출 실패) 질문의 낱말로 읽는다.
    // 가격 조건("5만원 이하", "3만원대")은 숫자라 어느 쪽이든 낱말로 읽는다
    private List<Plan> filter(String text, List<Plan> plans, boolean generalOnly, PlanQuestion.Conditions modelConditions) {
        Selected<Plan.NetworkType> networks = new Selected<>();
        Selected<Plan.TargetGroup> groups = new Selected<>();
        PlanQuestion.Unlimited unlimited;
        if (modelConditions != null) {
            networks.included.addAll(modelConditions.networks());
            networks.excluded.addAll(modelConditions.excludedNetworks());
            groups.included.addAll(modelConditions.targetGroups());
            groups.excluded.addAll(modelConditions.excludedTargetGroups());
            unlimited = modelConditions.unlimited();
        } else {
            // 뺀 요금제 이름은 지운 뒤 망·대상을 읽는다. "LTE 맥스 제외하고 LTE 요금제"를 LTE를 빼라는 말로 읽지 않게 한다
            String conditions = withoutExcludedNames(text, plans);
            networks.read(conditions, "5G", Plan.NetworkType.FIVE_G);
            networks.read(conditions, "LTE", Plan.NetworkType.LTE);
            networks.read(conditions, "알뜰", Plan.NetworkType.BUDGET);
            groups.read(conditions, AgeReplyParser.YOUTH_WORD, Plan.TargetGroup.YOUTH);
            groups.read(conditions, AgeReplyParser.SENIOR_WORD, Plan.TargetGroup.SENIOR);
            groups.read(conditions, "일반", Plan.TargetGroup.GENERAL);
            unlimited = text.contains("무제한") ? PlanQuestion.Unlimited.ONLY : PlanQuestion.Unlimited.ANY;
        }
        Matcher band = PRICE_BAND.matcher(text);
        Matcher under = PRICE_UNDER.matcher(text);
        Integer bandMin = band.find() ? Integer.parseInt(band.group(1)) * WON_PER_MAN : null;
        Integer max = under.find() ? Integer.parseInt(under.group(1)) * WON_PER_MAN : null;
        boolean exclusiveMax = max != null && "미만".equals(under.group(2));

        return plans.stream()
                .filter(plan -> networks.isEmpty() || networks.accepts(plan.getNetworkType()))
                .filter(plan -> groups.isEmpty() ? !generalOnly || plan.getTargetGroup() == Plan.TargetGroup.GENERAL
                        : groups.accepts(plan.getTargetGroup()))
                .filter(plan -> unlimited == PlanQuestion.Unlimited.ANY
                        || plan.isUnlimited() == (unlimited == PlanQuestion.Unlimited.ONLY))
                .filter(plan -> bandMin == null || plan.getMonthlyFee() >= bandMin && plan.getMonthlyFee() < bandMin + WON_PER_MAN)
                .filter(plan -> max == null || (exclusiveMax ? plan.getMonthlyFee() < max : plan.getMonthlyFee() <= max))
                .filter(plan -> modelConditions != null ? !modelConditions.excludedPlans().contains(plan.getCode())
                        : !excludedByName(text, plan, plans))
                .toList();
    }

    // 질문이 고른 값과 뺀 값. "청소년이나 시니어 말고"처럼 낱말 뒤에 제외 표현이 오면 뺀 값이다.
    // 고른 값이 있으면 그것만, 뺀 값만 있으면 나머지를 받는다
    private static final class Selected<T> {
        private final List<T> included = new ArrayList<>();
        private final List<T> excluded = new ArrayList<>();

        void read(String text, String word, T value) {
            read(text, Pattern.compile(Pattern.quote(word)), value);
        }

        void read(String text, Pattern words, T value) {
            Matcher word = words.matcher(text);
            if (!word.find()) {
                return;
            }
            (EXCLUDED_AFTER.matcher(text.substring(word.end())).lookingAt() ? excluded : included).add(value);
        }

        boolean isEmpty() {
            return included.isEmpty() && excluded.isEmpty();
        }

        boolean accepts(T value) {
            return included.isEmpty() ? !excluded.contains(value) : included.contains(value);
        }
    }

    private String extreme(List<Plan> matched, boolean cheapest, boolean groupAsked) {
        if (matched.isEmpty()) {
            return null;
        }
        Comparator<Plan> byFee = Comparator.comparingInt(Plan::getMonthlyFee);
        int fee = (cheapest ? matched.stream().min(byFee) : matched.stream().max(byFee)).orElseThrow().getMonthlyFee();
        List<Plan> picked = matched.stream().filter(plan -> plan.getMonthlyFee() == fee).toList();
        return "%s가장 %s 요금제예요.\n\n%s".formatted(groupAsked ? "" : "일반 요금제 중 ", cheapest ? "저렴한" : "비싼", list(picked));
    }

    private String detail(List<Plan> plans) {
        StringBuilder text = new StringBuilder();
        for (Plan plan : plans) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(plan.getName()).append("\n- 월정액 ").append(PlanFormat.money(plan.getMonthlyFee()))
                    .append("\n- 데이터 ").append(PlanFormat.data(plan.isUnlimited(), plan.getDataGb()))
                    .append("\n- 통화 ").append(PlanFormat.voice(plan.isVoiceUnlimited(), plan.getVoiceMinutes()))
                    .append("\n- 문자 ").append(PlanFormat.sms(plan.isSmsUnlimited(), plan.getSmsCount()));
        }
        String note = ageNote(plans);
        return note.isEmpty() ? text.toString() : text + "\n\n" + note;
    }

    private String list(List<Plan> plans) {
        Map<Plan.NetworkType, List<Plan>> byNetwork = new EnumMap<>(Plan.NetworkType.class);
        plans.forEach(plan -> byNetwork.computeIfAbsent(plan.getNetworkType(), key -> new ArrayList<>()).add(plan));
        StringBuilder text = new StringBuilder();
        byNetwork.forEach((network, group) -> {
            text.append("\n[").append(networkLabel(network)).append("]\n");
            group.forEach(plan -> text.append("- ").append(plan.getName()).append(": 월 ").append(PlanFormat.money(plan.getMonthlyFee()))
                    .append(" · 데이터 ").append(PlanFormat.data(plan.isUnlimited(), plan.getDataGb())).append('\n'));
        });
        String note = ageNote(plans);
        return (text.toString().stripLeading() + (note.isEmpty() ? "" : "\n" + note)).stripTrailing();
    }

    private String ageNote(List<Plan> plans) {
        boolean youth = plans.stream().anyMatch(plan -> plan.getTargetGroup() == Plan.TargetGroup.YOUTH);
        boolean senior = plans.stream().anyMatch(plan -> plan.getTargetGroup() == Plan.TargetGroup.SENIOR);
        List<String> notes = new ArrayList<>();
        if (youth) {
            notes.add("청소년 요금제는 만 %d~%d세".formatted(
                    SubscriptionEligibilityChecker.YOUTH_MIN_AGE, SubscriptionEligibilityChecker.YOUTH_MAX_AGE));
        }
        if (senior) {
            notes.add("시니어 요금제는 만 %d세 이상".formatted(SubscriptionEligibilityChecker.SENIOR_MIN_AGE));
        }
        return notes.isEmpty() ? "" : String.join(", ", notes) + "만 가입할 수 있어요.";
    }

    private static String networkLabel(Plan.NetworkType network) {
        return switch (network) {
            case FIVE_G -> "5G";
            case LTE -> "LTE";
            case BUDGET -> "알뜰";
        };
    }
}
