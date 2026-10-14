package com.telme.usage.service;

import com.telme.plan.entity.Plan;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 요금제 제공량. null이면 무제한이고 데이터는 MB, 통화는 분, 문자는 건 단위
public record UsageLimits(Long dataMb, Long voiceMinutes, Long smsCount) {

    private static final int MB_PER_GB = 1024;

    public static UsageLimits unlimited() {
        return new UsageLimits(null, null, null);
    }

    public static UsageLimits of(Plan plan) {
        return new UsageLimits(
                plan.isUnlimited() ? null : plan.getDataGb().multiply(BigDecimal.valueOf(MB_PER_GB))
                        .setScale(0, RoundingMode.HALF_UP).longValue(),
                plan.isVoiceUnlimited() ? null : Long.valueOf(plan.getVoiceMinutes()),
                plan.isSmsUnlimited() ? null : Long.valueOf(plan.getSmsCount()));
    }
}
