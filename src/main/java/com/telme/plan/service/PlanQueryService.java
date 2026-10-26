package com.telme.plan.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.plan.converter.PlanConverter;
import com.telme.plan.dto.res.PlanListResponse;
import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import com.telme.plan.exception.PlanErrorCode;
import com.telme.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService {

    private final PlanRepository planRepository;
    private final PlanConverter planConverter;

    // 판매 중인 요금제만 정렬 순서대로 내려준다. 15종 안팎이라 조건은 DB 대신 메모리에서 거른다
    public PlanListResponse getPlans(Plan.NetworkType networkType, Plan.TargetGroup targetGroup) {
        return planConverter.toListResponse(
                planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE).stream()
                        .filter(plan -> networkType == null || plan.getNetworkType() == networkType)
                        .filter(plan -> targetGroup == null || plan.getTargetGroup() == targetGroup)
                        .toList());
    }

    // 판매 종료된 요금제는 목록에서 빠지므로 상세도 없는 요금제와 같게 404로 응답한다
    public PlanResponse getPlan(long planId) {
        return planRepository.findByPlanIdAndStatus(planId, Plan.Status.ACTIVE)
                .map(planConverter::toResponse)
                .orElseThrow(() -> new GeneralException(PlanErrorCode.PLAN_NOT_FOUND));
    }
}
