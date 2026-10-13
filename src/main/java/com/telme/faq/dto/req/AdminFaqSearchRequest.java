package com.telme.faq.dto.req;

import com.telme.faq.entity.FaqCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminFaqSearchRequest(
        @Size(max = 200) String keyword,
        FaqCategory category,
        AdminFaqStatusFilter status,
        AdminFaqSort sort,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public AdminFaqSearchRequest {
        // 숨김·삭제 FAQ가 기본으로 섞이면 현행 문서를 가려내기 어렵다
        status = status == null ? AdminFaqStatusFilter.ACTIVE : status;
        sort = sort == null ? AdminFaqSort.RECENT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public String keywordPattern() {
        return FaqLikePatterns.keyword(keyword);
    }
    
    public String categoryPattern() {
        return FaqLikePatterns.category(category);
    }
}
