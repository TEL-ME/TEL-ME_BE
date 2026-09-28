package com.telme.faq.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Locale;

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
        if (isBlank(keyword)) {
            return MATCH_ALL;
        }
        // 서버 기본 Locale에 따라 대소문자 변환 결과가 달라지지 않게 ROOT 고정. 쿼리도 lower()로 맞춘다
        return MATCH_ALL + escapeLike(keyword.strip().toLowerCase(Locale.ROOT)) + MATCH_ALL;
    }

    public String categoryPattern() {
        return isBlank(category) ? MATCH_ALL : escapeLike(category.strip());
    }

    // LIKE에서 %와 _는 와일드카드라 그대로 넘기면 "10%" 검색이 "10" 검색이 된다.
    // 이스케이프 문자인 역슬래시를 먼저 바꿔야 뒤에서 붙인 역슬래시를 다시 바꾸지 않는다
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
