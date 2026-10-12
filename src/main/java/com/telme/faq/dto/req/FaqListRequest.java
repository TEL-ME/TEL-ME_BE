package com.telme.faq.dto.req;

import com.telme.faq.entity.FaqCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record FaqListRequest(
        @Size(max = 200) String keyword,
        FaqCategory category,
        FaqSort sort,
        @Min(0) Integer page,
        @Min(0) @Max(100) Integer size
        ) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String MATCH_ALL = "%";
    
    public FaqListRequest {
        // 자주 묻는 질문 화면이라 기본은 많이 쓰인 순이다
        sort = sort == null ? FaqSort.POPULAR : sort;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
    
    // 관리자 목록과 같은 쿼리를 써서 패턴도 같은 규칙으로 만든다
    public String keywordPattern() {
        if (keyword == null || keyword.isBlank()) {
            return MATCH_ALL;
        }
        return MATCH_ALL + AdminFaqSearchRequest.escapeLike(keyword.strip().toLowerCase(Locale.ROOT)) + MATCH_ALL;
    }
    
    public String categoryPattern() {
        return category == null ? MATCH_ALL : AdminFaqSearchRequest.escapeLike(category.name());
    }
}
