package com.telme.plan.converter;

import com.telme.plan.dto.res.PlanListResponse;
import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlanConverter {

    public PlanResponse toResponse(Plan plan) {
        return PlanResponse.builder()
                .planId(plan.getPlanId())
                .code(plan.getCode())
                .name(plan.getName())
                .networkType(plan.getNetworkType().name())
                .targetGroup(plan.getTargetGroup().name())
                .monthlyFee(plan.getMonthlyFee())
                .dataGb(plan.getDataGb())
                .unlimited(plan.isUnlimited())
                .build();
    }

    public PlanListResponse toListResponse(List<Plan> plans) {
        return new PlanListResponse(plans.stream().map(this::toResponse).toList());
    }
}
