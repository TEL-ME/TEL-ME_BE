package com.telme.llm.controller;

import com.telme.global.common.CustomResponse;
import com.telme.llm.dto.req.AdminLatencySearchRequest;
import com.telme.llm.dto.res.AdminLatencyResponse;
import com.telme.llm.service.AdminLatencyQueryService;
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

@Tag(name = "Admin System", description = "관리자 운영 상태")
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
public class AdminLatencyController {

    private final AdminLatencyQueryService adminLatencyQueryService;
    
    @Operation(
            summary = "응답 속도 조회",
            description = "기간 안의 응답 속도를 건수·평균·중앙값(p50)·p95(ms)로 반환합니다. 기간을 비우면 최근 24시간입니다.\n"
                    + "- overall: 질문을 받은 때부터 답변 저장까지(완료된 실행만)\n"
                    + "- firstToken: 답변 생성 LLM의 첫 토큰까지(스트리밍 호출, 성공만)\n"
                    + "- tasks: 작업 종류별 LLM 호출 시간(성공한 시도만). 호출이 없던 작업은 0건")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 시각 형식 오류, 시작 시각이 끝 시각보다 늦음(periodValid)"),
        @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
        @ApiResponse(responseCode = "403", description = "Admin 권한 없음")
    })
    @GetMapping("/latency")
    public CustomResponse<AdminLatencyResponse> getLatency(
            @ParameterObject @Valid @ModelAttribute AdminLatencySearchRequest request) {
        return CustomResponse.onSuccess(adminLatencyQueryService.getLatency(request));
    }
}
