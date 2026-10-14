package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
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
    private static final Pattern PRICE_BAND = Pattern.compile("(\\d+)만원대");
    private static final Pattern PRICE_UNDER = Pattern.compile("(\\d+)만원(이하|안쪽|이내|미만)");
    private static final int WON_PER_MAN = 10_000;

    private final PlanRepository planRepository;

    // 판매 중인 요금제가 없으면 null
    public String answer(String question) {
        List<Plan> plans = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
        if (plans.isEmpty()) {
            return null;
        }
        String text = question.replaceAll("\\s+", "");
        List<Plan> named = namedPlans(text, plans);
        if (!named.isEmpty()) {
            return detail(named);
        }
        boolean cheapest = CHEAPEST.matcher(text).find();
        boolean priciest = PRICIEST.matcher(text).find();
        List<Plan> matched = filter(text, plans, cheapest || priciest);
        if (cheapest || priciest) {
            return extreme(matched, cheapest, text);
        }
        if (matched.isEmpty()) {
            return "조건에 맞는 요금제를 찾지 못했어요. 판매 중인 전체 요금제는 아래와 같아요.\n\n" + list(plans);
        }
        String head = COUNT.matcher(text).find() ? "판매 중인 요금제는 총 %d종이에요.".formatted(matched.size()) : "요금제 안내예요.";
        return head + "\n\n" + list(matched);
    }

    private List<Plan> namedPlans(String text, List<Plan> plans) {
        List<Plan> named = new ArrayList<>();
        for (Plan plan : plans) {
            String name = plan.getName().replaceAll("\\s+", "");
            String alias = alias(plan, plans);
            if (text.contains(name) || alias != null && text.contains(alias)) {
                named.add(plan);
            }
        }
        return named;
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

    private List<Plan> filter(String text, List<Plan> plans, boolean generalOnly) {
        List<Plan.NetworkType> networks = new ArrayList<>();
        if (text.contains("5G")) networks.add(Plan.NetworkType.FIVE_G);
        if (text.toUpperCase().contains("LTE")) networks.add(Plan.NetworkType.LTE);
        if (text.contains("알뜰")) networks.add(Plan.NetworkType.BUDGET);
        List<Plan.TargetGroup> groups = new ArrayList<>();
        if (text.contains("청소년")) groups.add(Plan.TargetGroup.YOUTH);
        if (text.contains("시니어")) groups.add(Plan.TargetGroup.SENIOR);
        boolean unlimited = text.contains("무제한");
        Matcher band = PRICE_BAND.matcher(text);
        Matcher under = PRICE_UNDER.matcher(text);
        Integer bandMin = band.find() ? Integer.parseInt(band.group(1)) * WON_PER_MAN : null;
        Integer max = under.find() ? Integer.parseInt(under.group(1)) * WON_PER_MAN : null;
        boolean exclusiveMax = max != null && "미만".equals(under.group(2));

        return plans.stream()
                .filter(plan -> networks.isEmpty() || networks.contains(plan.getNetworkType()))
                .filter(plan -> groups.isEmpty() ? !generalOnly || plan.getTargetGroup() == Plan.TargetGroup.GENERAL
                        : groups.contains(plan.getTargetGroup()))
                .filter(plan -> !unlimited || plan.isUnlimited())
                .filter(plan -> bandMin == null || plan.getMonthlyFee() >= bandMin && plan.getMonthlyFee() < bandMin + WON_PER_MAN)
                .filter(plan -> max == null || (exclusiveMax ? plan.getMonthlyFee() < max : plan.getMonthlyFee() <= max))
                .toList();
    }

    private String extreme(List<Plan> matched, boolean cheapest, String text) {
        if (matched.isEmpty()) {
            return null;
        }
        Comparator<Plan> byFee = Comparator.comparingInt(Plan::getMonthlyFee);
        int fee = (cheapest ? matched.stream().min(byFee) : matched.stream().max(byFee)).orElseThrow().getMonthlyFee();
        List<Plan> picked = matched.stream().filter(plan -> plan.getMonthlyFee() == fee).toList();
        boolean groupAsked = text.contains("청소년") || text.contains("시니어");
        return "%s가장 %s 요금제예요.\n\n%s".formatted(groupAsked ? "" : "일반 요금제 중 ", cheapest ? "저렴한" : "비싼", list(picked));
    }

    private String detail(List<Plan> plans) {
        StringBuilder text = new StringBuilder();
        for (Plan plan : plans) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(plan.getName()).append("\n- 월정액 ").append(PlanFormat.money(plan.getMonthlyFee()))
                    .append("\n- 데이터 ").append(PlanFormat.data(plan.isUnlimited(), plan.getDataGb()));
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
