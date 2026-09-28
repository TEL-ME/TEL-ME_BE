package com.telme.faq.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminFaqSearchRequest(
        @Size(max = 200) String keyword,
        @Size(max = 30) String category,
        AdminFaqStatusFilter status,
        AdminFaqSort sort,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String MATCH_ALL = "%";

    public AdminFaqSearchRequest {
        // 숨김·삭제 FAQ가 기본으로 섞이면 현행 문서를 가려내기 어렵다
        status = status == null ? AdminFaqStatusFilter.ACTIVE : status;
        sort = sort == null ? AdminFaqSort.RECENT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    // 조건을 안 준 항목은 "%"로 넘겨 쿼리에서 null 분기를 없앤다. question·answer·category 모두 NOT NULL
    public String keywordPattern() {
        return isBlank(keyword) ? MATCH_ALL : MATCH_ALL + keyword.strip().toLowerCase() + MATCH_ALL;
    }

    public String categoryPattern() {
        return isBlank(category) ? MATCH_ALL : category.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
