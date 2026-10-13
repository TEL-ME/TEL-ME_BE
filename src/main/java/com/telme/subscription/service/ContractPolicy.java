package com.telme.subscription.service;

public final class ContractPolicy {

    public static final int CONTRACT_MONTHS = 24;
    public static final int DISCOUNT_PERCENT = 25;

    private static final int FULL_REFUND_UNDER_MONTHS = 12;
    private static final int FULL_REFUND_PERCENT = 100;
    private static final int HALF_REFUND_PERCENT = 50;

    private ContractPolicy() {
    }

    public static int refundPercent(int elapsedMonths, int contractMonths) {
        if (elapsedMonths < FULL_REFUND_UNDER_MONTHS) {
            return FULL_REFUND_PERCENT;
        }
        return elapsedMonths < contractMonths ? HALF_REFUND_PERCENT : 0;
    }
}
