package com.telme.dashboard.controller;

import com.telme.dashboard.dto.req.AdminDashboardSearchRequest;
import com.telme.dashboard.dto.req.AdminIntentDistributionSearchRequest;
import com.telme.dashboard.dto.res.AdminDashboardDailyResponse;
import com.telme.dashboard.dto.res.AdminDashboardResponse;
import com.telme.dashboard.dto.res.AdminIntentDistributionResponse;
import com.telme.dashboard.service.AdminDashboardQueryService;
import com.telme.dashboard.service.AdminIntentDistributionQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 요약")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardQueryService adminDashboardQueryService;
    private final AdminIntentDistributionQueryService adminIntentDistributionQueryService;

    @Operation(summary = "의도 분류 분포 조회",
            description = "저장된 분류 기록의 FAQ/STORE/BOTH/UNKNOWN 및 RULE/LLM/UNRECORDED 건수·비율입니다. "
                    + "분류 기록 시각 기준 from 이상, to 미만이며 생략한 경계는 전체 기간입니다. "
                    + "날짜 선택은 KST 기준 시각을 오프셋과 함께 전달합니다. 시작과 끝이 같으면 0건입니다. "
                    + "인사·일부 후속 응답·저장 전 거절된 질문은 빠질 수 있어 전체 고객 입력 수와 다릅니다. "
                    + "RULE에는 장애 대체와 규칙 보정이 포함되므로 LLM 장애율·미호출 비율이 아닙니다. "
                    + "비율은 전체 기록 대비 백분율이며 소수점 둘째 자리 HALF_UP으로 반올림합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: 시각 형식 오류. "
                    + "DASHBOARD400-0: 기간의 시작이 끝보다 늦음"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping("/intent-distribution")
    public CustomResponse<AdminIntentDistributionResponse> getIntentDistribution(
            @Valid @ParameterObject @ModelAttribute AdminIntentDistributionSearchRequest request) {
        return CustomResponse.onSuccess(adminIntentDistributionQueryService.getDistribution(request));
    }

    @Operation(
            summary = "대시보드 요약 조회",
            description = "답 못 한 질문 수, 미처리 싫어요 수, 실패한 답변 수, 오늘·어제 질문 수를 한 번에 반환합니다. "
                    + "from·to는 답 못 한 질문 수와 실패한 답변 수에만 걸립니다. 미처리 싫어요는 처리하면 줄어드는 "
                    + "숫자라 기간과 상관없이 전부 세고, 오늘·어제 질문 수는 한국 시간 자정으로 끊습니다. "
                    + "증감은 오늘·어제 두 숫자로 화면에서 계산합니다. 미리보기 목록은 각 목록 API에 size를 주면 됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: 시각 형식 오류. "
                    + "DASHBOARD400-0: 기간의 시작이 끝보다 늦음"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminDashboardResponse> getSummary(
            @Valid @ParameterObject @ModelAttribute AdminDashboardSearchRequest request) {
        return CustomResponse.onSuccess(adminDashboardQueryService.getSummary(request));
    }
    
    @Operation(
            summary = "최근 7일 날짜별 질문·오류 수",
            description = "오늘을 포함한 최근 7일의 하루 질문 수와 LLM 호출 오류 수를 오래된 날부터 반환합니다. "
                    + "날짜는 한국 시간 자정으로 끊고, 기록이 없는 날도 0으로 들어 있습니다. "
                    + "오류 수는 운영 상태의 오류 목록과 같은 기준이라 재시도한 시도도 각각 셉니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping("/daily")
    public CustomResponse<AdminDashboardDailyResponse> getDaily() {
        return CustomResponse.onSuccess(adminDashboardQueryService.getDaily());
    }
}
