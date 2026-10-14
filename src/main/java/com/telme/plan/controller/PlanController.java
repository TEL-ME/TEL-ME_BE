package com.telme.plan.controller;

import com.telme.global.common.CustomResponse;
import com.telme.plan.dto.res.PlanListResponse;
import com.telme.plan.dto.res.PlanResponse;
import com.telme.plan.entity.Plan;
import com.telme.plan.service.PlanQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Plan", description = "요금제 카탈로그 API")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanQueryService planQueryService;

    @Operation(
            summary = "요금제 목록 조회",
            description = "판매 중인 요금제를 정렬 순서대로 반환합니다. networkType(FIVE_G, LTE, BUDGET)과 "
                    + "targetGroup(GENERAL, YOUTH, SENIOR)으로 거를 수 있고, 생략하면 전체를 반환합니다. "
                    + "무제한 요금제는 unlimited가 true이고 dataGb가 null입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공. 조건에 맞는 요금제가 없으면 plans가 빈 배열"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: networkType 또는 targetGroup 값 오류")
    })
    @GetMapping
    public CustomResponse<PlanListResponse> getPlans(
            @Parameter(description = "망 구분") @RequestParam(required = false) Plan.NetworkType networkType,
            @Parameter(description = "대상 구분") @RequestParam(required = false) Plan.TargetGroup targetGroup) {
        return CustomResponse.onSuccess(planQueryService.getPlans(networkType, targetGroup));
    }

    @Operation(summary = "요금제 상세 조회", description = "요금제 한 건을 반환합니다. 판매 종료된 요금제는 404입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 요금제 ID 형식 오류 또는 1 미만"),
        @ApiResponse(responseCode = "404", description = "PLAN404-0: 요금제가 없거나 판매 종료")
    })
    @GetMapping("/{planId}")
    public CustomResponse<PlanResponse> getPlan(
            @Parameter(description = "조회할 요금제 ID") @PathVariable @Positive(message = "{plan.id.positive}") long planId) {
        return CustomResponse.onSuccess(planQueryService.getPlan(planId));
    }
}
