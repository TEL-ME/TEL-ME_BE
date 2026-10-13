package com.telme.subscription.controller;

import com.telme.global.common.CustomResponse;
import com.telme.member.service.CurrentMemberResolver;
import com.telme.subscription.converter.SubscriptionConverter;
import com.telme.subscription.dto.req.ChangePlanRequest;
import com.telme.subscription.dto.req.PauseRequest;
import com.telme.subscription.dto.req.SubscribeRequest;
import com.telme.subscription.dto.res.SubscriptionMeResponse;
import com.telme.subscription.dto.res.SubscriptionResponse;
import com.telme.subscription.dto.res.TerminationPenaltyResponse;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionPauseService;
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

@Tag(name = "Subscription", description = "요금제 가입·변경·해지·일시 정지 API (로그인 필요)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final CurrentMemberResolver currentMemberResolver;
    private final SubscriptionCommandService commandService;
    private final SubscriptionPauseService pauseService;
    private final SubscriptionQueryService queryService;
    private final SubscriptionConverter subscriptionConverter;

    @Operation(
            summary = "요금제 가입",
            description = "요금제에 가입합니다. 온라인 신청은 다음 날 09:00(KST)에 개통되며, 개통 전에는 currentPlan이 null이고 "
                    + "pendingActivation에 개통 예정 요금제와 시각이 내려갑니다. 청소년(만 14~18세)·시니어(만 65세 이상) 요금제는 "
                    + "만 나이를 확인하며, 저장된 생년월일이 없으면 birthDate가 필요합니다. 저장된 생년월일이 있으면 요청 값은 쓰지 않습니다. "
                    + "contract가 true이면 24개월 약정으로 가입하고 월정액의 25%를 할인받으며, 응답의 contract에 할인액이 내려갑니다.")
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
            @Valid @RequestBody SubscribeRequest request, HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(
                commandService.subscribe(userId, request.planId(), request.birthDate(), request.contract())));
    }

    @Operation(
            summary = "요금제 변경",
            description = "요금제는 매월 1회, 가입 다음 달부터 변경할 수 있고 신청 다음 날 0시(KST)부터 적용됩니다. 개통 전과 일시 정지 중에는 "
                    + "변경할 수 없습니다. 응답의 pendingChange가 적용 전인 변경입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 신청 성공"),
        @ApiResponse(responseCode = "400", description = "SUBSCRIPTION400-0·1·2: 청소년·시니어 자격 오류, COMMON400-1: 값 형식 오류"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음, PLAN404-0: 요금제가 없거나 판매 종료"),
        @ApiResponse(responseCode = "409", description = "SUBSCRIPTION409-1: 현재와 같은 요금제, SUBSCRIPTION409-2: 가입한 달, "
                + "SUBSCRIPTION409-3: 이번 달 이미 변경, SUBSCRIPTION409-4: 개통 전, SUBSCRIPTION409-8: 일시 정지 중")
    })
    @PatchMapping("/plan")
    public CustomResponse<SubscriptionResponse> changePlan(
            @Valid @RequestBody ChangePlanRequest request, HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(
                commandService.changePlan(userId, request.planId(), request.birthDate())));
    }

    @Operation(
            summary = "요금제 해지",
            description = "신청 즉시 해지됩니다. 개통 전과 일시 정지 중에도 해지할 수 있고, 적용 전인 변경은 함께 사라집니다. "
                    + "약정 중이면 위약금이 확정되어 응답에 내려가며, 해지 전에 /me/termination-penalty로 미리 확인할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "해지 성공. 약정이 없으면 위약금 0원"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음")
    })
    @DeleteMapping
    public CustomResponse<TerminationPenaltyResponse> cancel(HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toPenaltyResponse(commandService.cancel(userId)));
    }

    @Operation(
            summary = "내 요금제 조회",
            description = "가입하지 않았으면 subscribed가 false이고 subscription이 null입니다(오류 아님). "
                    + "가입 중이면 현재 요금제(개통 전이면 null), 약정(contract), 개통 예정(pendingActivation), "
                    + "적용 전인 변경(pendingChange), 일시 정지 중이면 정지 정보(pause)를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/me")
    public CustomResponse<SubscriptionMeResponse> getMine(HttpServletRequest httpRequest) {
        return CustomResponse.onSuccess(queryService.getMine(currentMemberResolver.resolve(httpRequest).getUserId()));
    }

    @Operation(
            summary = "해지 전 예상 위약금 조회",
            description = "지금 해지하면 내야 할 위약금을 계산해 반환하며 상태는 바꾸지 않습니다. 가입일 기준 12개월 미만이면 받은 "
                    + "할인액의 100%, 12개월 이상 24개월 미만이면 50%이고, 약정이 없거나 24개월을 채웠으면 0원입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공. hasContract가 false이면 약정 없음"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음")
    })
    @GetMapping("/me/termination-penalty")
    public CustomResponse<TerminationPenaltyResponse> getTerminationPenalty(HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(
                subscriptionConverter.toPenaltyResponse(queryService.getTerminationPenalty(userId)));
    }

    @Operation(
            summary = "일시 정지 신청",
            description = "신청 시각부터 days일 동안 일시 정지합니다. 1회 최대 30일, 연간(달력 연도) 합산 최대 90일이며 횟수 제한은 "
                    + "없습니다. 정지 기간에는 기본료의 50%가 부과됩니다(응답 pause.reducedMonthlyFee). 기간이 끝나면 자동으로 "
                    + "복귀하고, 더 정지하려면 다시 신청해야 합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "정지 신청 성공"),
        @ApiResponse(responseCode = "400", description = "SUBSCRIPTION400-3: 정지 일수가 1~30일이 아님, COMMON400-1: 값 형식 오류"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음"),
        @ApiResponse(responseCode = "409", description = "SUBSCRIPTION409-5: 개통 전, SUBSCRIPTION409-6: 이미 정지 중, "
                + "SUBSCRIPTION409-7: 연간 90일 초과")
    })
    @PostMapping("/pause")
    public CustomResponse<SubscriptionResponse> pause(
            @Valid @RequestBody PauseRequest request, HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(pauseService.pause(userId, request.days())));
    }

    @Operation(summary = "일시 정지 해제", description = "정지를 바로 끝내고 복귀합니다. 실제로 정지한 일수만 연간 한도에 반영됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "해제 성공"),
        @ApiResponse(responseCode = "401", description = "로그인 필요"),
        @ApiResponse(responseCode = "404", description = "SUBSCRIPTION404-0: 가입한 요금제 없음, SUBSCRIPTION404-1: 일시 정지 중이 아님")
    })
    @DeleteMapping("/pause")
    public CustomResponse<SubscriptionResponse> resume(HttpServletRequest httpRequest) {
        Long userId = currentMemberResolver.resolve(httpRequest).getUserId();
        return CustomResponse.onSuccess(subscriptionConverter.toResponse(pauseService.resume(userId)));
    }
}
