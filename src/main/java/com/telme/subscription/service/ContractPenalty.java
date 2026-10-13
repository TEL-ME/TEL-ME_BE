package com.telme.subscription.service;

// 약정이 없으면 contractMonths가 null이고 나머지는 0
public record ContractPenalty(
        Integer contractMonths,
        int elapsedMonths,
        long receivedDiscount,
        int refundPercent,
        long penalty
) {

    public static ContractPenalty none() {
        return new ContractPenalty(null, 0, 0, 0, 0);
    }
}
