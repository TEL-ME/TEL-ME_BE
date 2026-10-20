package com.telme.consult.service;

import com.telme.plan.entity.Plan;
import com.telme.usage.service.UsageLimits;
import java.util.Comparator;
import java.util.List;

/** 평소 사용량에 여유를 둬서 한도를 채우는 가장 싼 요금제를 고른다. */
final class PlanRecommender {

    static final double MARGIN = 1.2;
    // 더 싼 요금제로 내릴 때는 한도 경계에서 오가지 않게 여유를 더 둔다
    static final double DOWNGRADE_MARGIN = 1.4;

    enum Kind { KEEP, CHEAPER, UPGRADE }

    record Demand(long dataMb, long voiceMinutes, long smsCount) {
    }

    record Recommendation(Plan plan, Kind kind) {
    }

    // fiveG는 가장 싼 요금제가 5G가 아닐 때 5G 중 가장 싼 요금제이고, 가장 싼 요금제가 5G면 null
    record UsageFit(Plan cheapest, Plan fiveG) {
    }

    private static final Comparator<Plan> CHEAPEST_THEN_MOST_DATA =
            Comparator.comparingInt(Plan::getMonthlyFee).thenComparing(PlanRecommender::dataMb, Comparator.reverseOrder());

    // allowDowngrade가 false이면 지금 요금제가 알맞은 동안은 더 싼 요금제를 권하지 않는다
    Recommendation recommend(List<Plan> candidates, Plan current, Demand demand, boolean allowDowngrade) {
        Plan best = cheapestFitting(candidates, demand, MARGIN);
        if (best == null) {
            Plan biggest = candidates.stream().max(Comparator.comparingDouble(PlanRecommender::dataMb)).orElse(current);
            return dataMb(biggest) > dataMb(current) ? new Recommendation(biggest, Kind.UPGRADE) : keep(current);
        }
        if (fits(current, demand, MARGIN)) {
            if (!allowDowngrade) {
                return keep(current);
            }
            Plan cheaper = cheapestFitting(candidates, demand, DOWNGRADE_MARGIN);
            return cheaper != null && cheaper.getMonthlyFee() < current.getMonthlyFee()
                    ? new Recommendation(cheaper, Kind.CHEAPER) : keep(current);
        }
        return new Recommendation(best, best.getMonthlyFee() < current.getMonthlyFee() ? Kind.CHEAPER : Kind.UPGRADE);
    }

    // 고객이 말한 사용량에 여유를 둔 가장 싼 요금제와 5G 중 가장 싼 요금제. 후보가 없으면 null
    UsageFit forUsage(List<Plan> candidates, Demand demand) {
        Plan cheapest = cheapestFittingOrBiggest(candidates, demand);
        if (cheapest == null) {
            return null;
        }
        if (cheapest.getNetworkType() == Plan.NetworkType.FIVE_G) {
            return new UsageFit(cheapest, null);
        }
        List<Plan> fiveG = candidates.stream().filter(plan -> plan.getNetworkType() == Plan.NetworkType.FIVE_G).toList();
        return new UsageFit(cheapest, cheapestFittingOrBiggest(fiveG, demand));
    }

    // 채우는 요금제가 없으면 데이터가 가장 큰 요금제
    private static Plan cheapestFittingOrBiggest(List<Plan> candidates, Demand demand) {
        Plan fitting = cheapestFitting(candidates, demand, MARGIN);
        return fitting != null ? fitting
                : candidates.stream().max(Comparator.comparingDouble(PlanRecommender::dataMb)).orElse(null);
    }

    private static Plan cheapestFitting(List<Plan> candidates, Demand demand, double margin) {
        return candidates.stream().filter(plan -> fits(plan, demand, margin)).min(CHEAPEST_THEN_MOST_DATA).orElse(null);
    }

    static boolean fits(Plan plan, Demand demand) {
        return fits(plan, demand, MARGIN);
    }

    static boolean fits(Plan plan, Demand demand, double margin) {
        UsageLimits limits = UsageLimits.of(plan);
        return enough(limits.dataMb(), demand.dataMb(), margin) && enough(limits.voiceMinutes(), demand.voiceMinutes(), margin)
                && enough(limits.smsCount(), demand.smsCount(), margin);
    }

    private static boolean enough(Long limit, long used, double margin) {
        return limit == null || limit >= used * margin;
    }

    private static Recommendation keep(Plan current) {
        return new Recommendation(current, Kind.KEEP);
    }

    private static double dataMb(Plan plan) {
        Long limit = UsageLimits.of(plan).dataMb();
        return limit == null ? Double.MAX_VALUE : limit;
    }
}
