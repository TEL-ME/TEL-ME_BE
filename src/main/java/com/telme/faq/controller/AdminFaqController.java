package com.telme.faq.controller;

import com.telme.faq.dto.req.AdminFaqSearchRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.service.AdminFaqQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Faq", description = "관리자 FAQ 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/faqs")
public class AdminFaqController {

    private final AdminFaqQueryService adminFaqQueryService;

    @Operation(
            summary = "FAQ 목록 조회",
            description = "검색어·카테고리·상태로 거른 FAQ를 인용 횟수와 함께 반환합니다. "
                    + "카테고리는 10종 코드 중 하나이고, 상태 기본값은 ACTIVE이며 ALL을 주면 숨김·삭제까지 모두 봅니다. "
                    + "정렬은 RECENT(최근 수정 순), CITATION_DESC, CITATION_ASC 중 하나입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-1: enum 값, 길이 또는 범위 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @GetMapping
    public CustomResponse<AdminFaqListResponse> getFaqs(
            @Valid @ParameterObject @ModelAttribute AdminFaqSearchRequest request) {
        return CustomResponse.onSuccess(adminFaqQueryService.getFaqs(request));
    }

    @Operation(summary = "FAQ 단건 조회", description = "답변 본문과 버전, 내용 해시, 인용 횟수를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음")
    })
    @GetMapping("/{faqId}")
    public CustomResponse<AdminFaqDetailResponse> getFaq(
            @Parameter(description = "조회할 FAQ ID") @PathVariable long faqId) {
        return CustomResponse.onSuccess(adminFaqQueryService.getFaq(faqId));
    }
}
