package com.telme.consult.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PlanFormat {

    private static final int MB_PER_GB = 1024;

    private PlanFormat() {
    }

    static String money(long won) {
        return "%,d원".formatted(won);
    }

    static String data(boolean unlimited, BigDecimal dataGb) {
        return unlimited ? "무제한" : dataGb.stripTrailingZeros().toPlainString() + "GB";
    }

    static String voice(boolean unlimited, Integer minutes) {
        return unlimited ? "무제한" : minutes + "분";
    }

    static String sms(boolean unlimited, Integer count) {
        return unlimited ? "무제한" : count + "건";
    }

    static String megabytes(long mb) {
        return BigDecimal.valueOf(mb).divide(BigDecimal.valueOf(MB_PER_GB), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "GB";
    }
}
