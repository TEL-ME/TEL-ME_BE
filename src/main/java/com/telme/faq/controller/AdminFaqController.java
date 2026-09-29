package com.telme.faq.controller;

import com.telme.faq.dto.req.AdminFaqSaveRequest;
import com.telme.faq.dto.req.AdminFaqSearchRequest;
import com.telme.faq.dto.res.AdminFaqDetailResponse;
import com.telme.faq.dto.res.AdminFaqListResponse;
import com.telme.faq.service.AdminFaqCommandService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Faq", description = "관리자 FAQ 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/faqs")
public class AdminFaqController {

    private final AdminFaqQueryService adminFaqQueryService;
    private final AdminFaqCommandService adminFaqCommandService;

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

    @Operation(
            summary = "FAQ 등록",
            description = "저장하면서 content_hash를 계산하고 임베딩을 만듭니다. "
                    + "임베딩 생성에 실패하면 등록도 취소됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: enum 값 오류. COMMON400-1: 필수값 또는 길이 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "503", description = "FAQ503-0: 임베딩 서버 호출 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<AdminFaqDetailResponse> createFaq(
            @Valid @RequestBody AdminFaqSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(
                HttpStatus.CREATED, adminFaqCommandService.create(request, adminId));
    }

    @Operation(
            summary = "FAQ 수정",
            description = "질문이나 답변이 바뀐 경우에만 content_hash와 version이 올라갑니다. "
                    + "카테고리만 바꾸면 버전은 그대로이고, 임베딩에 카테고리를 넣는 구성에서만 다시 만듭니다. "
                    + "status를 생략하면 기존 상태를 그대로 둡니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "COMMON400-0: enum 값 오류. COMMON400-1: 필수값 또는 길이 오류"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음"),
            @ApiResponse(responseCode = "503", description = "FAQ503-0: 임베딩 서버 호출 실패")
    })
    @PutMapping("/{faqId}")
    public CustomResponse<AdminFaqDetailResponse> updateFaq(
            @Parameter(description = "수정할 FAQ ID") @PathVariable long faqId,
            @Valid @RequestBody AdminFaqSaveRequest request,
            @AuthenticationPrincipal Long adminId) {
        return CustomResponse.onSuccess(adminFaqCommandService.update(faqId, request, adminId));
    }

    @Operation(
            summary = "FAQ 삭제",
            description = "실제로 지우지 않고 상태를 DELETED로 바꿉니다. 검색에서는 바로 빠지고 상태 필터로 다시 볼 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ를 찾을 수 없음")
    })
    @DeleteMapping("/{faqId}")
    public CustomResponse<Void> deleteFaq(
            @Parameter(description = "삭제할 FAQ ID") @PathVariable long faqId,
            @AuthenticationPrincipal Long adminId) {
        adminFaqCommandService.delete(faqId, adminId);
        return CustomResponse.onSuccess(null);
    }
}
