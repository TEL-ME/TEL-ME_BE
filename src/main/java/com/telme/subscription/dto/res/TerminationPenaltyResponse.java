package com.telme.subscription.dto.res;

// 약정이 없으면 hasContract가 false이고 contractMonths는 null, 나머지는 0
public record TerminationPenaltyResponse(
        boolean hasContract,
        Integer contractMonths,
        int elapsedMonths,
        long receivedDiscount,
        int refundPercent,
        long penalty
) {
}
