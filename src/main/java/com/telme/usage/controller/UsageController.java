package com.telme.usage.controller;

import com.telme.global.common.CustomResponse;
import com.telme.member.service.CurrentMemberResolver;
import com.telme.usage.service.SubscriptionUsageService;
import com.telme.usage.service.UsageOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usage", description = "이번 달 사용량·예상 요금 조회 API (로그인 필요)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final CurrentMemberResolver currentMemberResolver;
    private final SubscriptionUsageService usageService;

    @Operation(
            summary = "내 이번 달 사용량 조회",
            description = "status로 상태를 구분하며 미가입(NOT_SUBSCRIBED)과 개통 전(PENDING_ACTIVATION, activatesAt에 개통 시각)은 "
                    + "오류가 아니고 usage가 null입니다. 개통 후(ACTIVE)에는 데이터(MB)·통화(분)·문자(건)의 사용량·한도·남은 양·사용률, "
                    + "정산일(말일)까지 남은 일수(daysUntilBilling), 일시 정지 여부(paused), 예상 요금(expectedFee)을 반환합니다. "
                    + "무제한 항목은 limit·remaining·percent가 null이고 unlimited가 true이며, 한도를 넘으면 exceeded가 true이고 "
                    + "사용률이 100을 넘습니다(추가 요금 없음). 사용량은 실제 통신망이 없어 서비스가 사용자별로 생성해 저장한 값이고, "
                    + "이번 달은 개통 이후 지난 일수만큼만 반영합니다. 예상 요금은 월정액에서 약정 할인(진행 중일 때만)을 뺀 값이며 "
                    + "previousAmount·difference는 지난달에 가입 전이었으면 null입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/me")
    public CustomResponse<UsageOverview> getMine(HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(usageService.getOverview(userId));
    }
}
