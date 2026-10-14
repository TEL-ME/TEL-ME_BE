package com.telme.faq.controller;

import com.telme.faq.dto.req.FaqListRequest;
import com.telme.faq.dto.res.FaqListResponse;
import com.telme.faq.dto.res.FaqResponse;
import com.telme.faq.service.FaqQueryService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Faq", description = "FAQ 조회 API")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/faqs")
public class FaqController {

    private final FaqQueryService faqQueryService;
    
    @Operation(
            summary = "FAQ 목록 조회",
            description = "공개 중인 FAQ를 검색어(질문·답변)와 카테고리로 걸러 반환합니다. "
                    + "정렬은 POPULAR(자주 묻는 순, 기본값), RECENT(최근 수정 순) 중 하나입니다. "
                    + "로그인하지 않아도 조회할 수 있습니다. 개수는 기본 20개, 최대 100개입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공. 결과가 없으면 faqs가 빈 배열"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 카테고리·정렬 코드, 길이 또는 범위 오류")
    })
    @GetMapping
    public CustomResponse<FaqListResponse> getFaqs(@Valid @ParameterObject @ModelAttribute FaqListRequest request) {
        return CustomResponse.onSuccess(faqQueryService.getFaqs(request));
    }
    
    @Operation(
            summary = "FAQ 상세 조회",
            description = "질문과 답변을 반환합니다. 숨김·삭제한 FAQ는 404입니다."
            )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: FAQ ID 형식 오류 또는 1 미만"),
        @ApiResponse(responseCode = "404", description = "FAQ404-0: FAQ가 없거나 공개 중이 아님")
    })
    @GetMapping("/{faqId}")
    public CustomResponse<FaqResponse> getFaq(
            @Parameter(description = "조회할 FAQ ID") @PathVariable
            @Positive(message = "{faq.id.positive}") long faqId) {
        return CustomResponse.onSuccess(faqQueryService.getFaq(faqId));
    }
}
