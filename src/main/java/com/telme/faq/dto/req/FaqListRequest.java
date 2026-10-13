package com.telme.faq.dto.req;

import com.telme.faq.entity.FaqCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record FaqListRequest(
        @Size(max = 200) String keyword,
        FaqCategory category,
        FaqSort sort,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size
        ) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    
    public FaqListRequest {
        // 자주 묻는 질문 화면이라 기본은 많이 쓰인 순이다
        sort = sort == null ? FaqSort.POPULAR : sort;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
    
    // 관리자 목록과 같은 쿼리를 써서 패턴도 같은 규칙으로 만든다
    public String keywordPattern() {
            return FaqLikePatterns.keyword(keyword);
        }
    
    public String categoryPattern() {
        return FaqLikePatterns.category(category);
    }
}
