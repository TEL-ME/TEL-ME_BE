package com.telme.subscription.controller;

import com.telme.global.common.CustomResponse;
import com.telme.member.service.CurrentMemberResolver;
import com.telme.subscription.converter.SubscriptionConverter;
import com.telme.subscription.dto.req.SubscriptionRequest;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Subscription", description = "요금제 가입·변경·해지 API (로그인 필요)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final CurrentMemberResolver currentMemberResolver;
    private final SubscriptionCommandService commandService;
    private final SubscriptionQueryService queryService;
    private final SubscriptionConverter subscriptionConverter;

    @Operation(
            summary = "요금제 가입",
            description = "요금제에 가입하고 바로 적용됩니다. 청소년·시니어 요금제는 만 나이를 확인하며, 저장된 생년월일이 없으면 "
                    + "birthDate가 필요합니다. 저장된 생년월일이 있으면 요청 값은 쓰지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "가입 성공"),
        @ApiResponse(responseCode = "400", description = "SUBSCRIPTION400-0: 생년월일 필요, SUBSCRIPTION400-1: 생년월일 오류, "
                + "SUBSCRIPTION400-2: 가입 대상 연령 아님, COMMON400-1: 값 형식 오류"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "PLAN404-0: 요금제가 없거나 판매 종료"),
        @ApiResponse(responseCode = "409", description = "SUBSCRIPTION409-0: 이미 가입한 요금제가 있음")
    })
    @PostMapping
    public CustomResponse<SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscriptionRequest request, HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(
                commandService.subscribe(userId, request.planId(), request.birthDate())));
    }

    @Operation(
            summary = "요금제 변경",
            description = "요금제는 매월 1회, 가입 다음 달부터 변경할 수 있고 신청 다음 날 0시(KST)부터 적용됩니다. "
                    + "응답의 pendingChange가 적용 전인 변경입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 신청 성공"),
        @ApiResponse(responseCode = "400", description = "SUBSCRIPTION400-0·1·2: 청소년·시니어 자격 오류, COMMON400-1: 값 형식 오류"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음, PLAN404-0: 요금제가 없거나 판매 종료"),
        @ApiResponse(responseCode = "409", description = "SUBSCRIPTION409-1: 현재와 같은 요금제, SUBSCRIPTION409-2: 가입한 달, "
                + "SUBSCRIPTION409-3: 이번 달 이미 변경")
    })
    @PatchMapping("/plan")
    public CustomResponse<SubscriptionResponse> changePlan(
            @Valid @RequestBody SubscriptionRequest request, HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(
                commandService.changePlan(userId, request.planId(), request.birthDate())));
    }

    @Operation(summary = "요금제 해지", description = "신청 즉시 해지됩니다. 적용 전인 변경이 있어도 함께 사라집니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "해지 성공"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음")
    })
    @DeleteMapping
    public CustomResponse<Void> cancel(HttpServletRequest httpRequest) {
        commandService.cancel(currentMemberResolver.resolve(httpRequest).getUserId());
        return CustomResponse.onSuccess(null);
    }

    @Operation(
            summary = "내 요금제 조회",
            description = "가입하지 않았으면 subscribed가 false이고 subscription이 null입니다(오류 아님). "
                    + "가입 중이면 현재 요금제와 적용 전인 변경(pendingChange)을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/me")
    public CustomResponse<SubscriptionMeResponse> getMine(HttpServletRequest httpRequest) {
        return CustomResponse.onSuccess(queryService.getMine(currentMemberResolver.resolve(httpRequest).getUserId()));
    }
}
