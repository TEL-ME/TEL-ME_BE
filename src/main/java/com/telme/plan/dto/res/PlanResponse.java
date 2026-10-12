package com.telme.plan.dto.res;

import java.math.BigDecimal;
import lombok.Builder;

// dataGb는 무제한이면 null이고 unlimited가 true
@Builder
public record PlanResponse(
        Long planId,
        String code,
        String name,
        String networkType,
        String targetGroup,
        int monthlyFee,
        BigDecimal dataGb,
        boolean unlimited
) {
}
