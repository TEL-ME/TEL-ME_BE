package com.telme.faq.controller;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.global.common.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Faq Search", description = "내부 검색 테스트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/faq")
@ConditionalOnProperty(name = "faq.search-test-api-enabled", havingValue = "true")
public class FaqSearchController {

    private final FaqSearchService faqSearchService;

    @Operation(summary = "질문으로 FAQ 검색", description = "질문 문자열로 top-K FAQ를 score와 함께 반환한다.")
    @GetMapping("/search")
    public CustomResponse<List<FaqSearchResponse>> search(@Valid @ParameterObject @ModelAttribute FaqSearchRequest request) {
        return CustomResponse.onSuccess(faqSearchService.search(request));
    }
}
