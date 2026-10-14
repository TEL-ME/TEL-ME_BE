package com.telme.consult.service;

import java.math.BigDecimal;

final class PlanFormat {

    private PlanFormat() {
    }

    static String money(long won) {
        return "%,d원".formatted(won);
    }

    static String data(boolean unlimited, BigDecimal dataGb) {
        return unlimited ? "무제한" : dataGb.stripTrailingZeros().toPlainString() + "GB";
    }
}
