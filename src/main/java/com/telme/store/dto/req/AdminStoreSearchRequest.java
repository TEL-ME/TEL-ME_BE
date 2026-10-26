package com.telme.store.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record AdminStoreSearchRequest(
        @Size(max = 100) String keyword,
        AdminStoreStatusFilter status,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
        ) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String MATCH_ALL = "%";
    
    public AdminStoreSearchRequest {
        // 삭제(폐점)한 매장이 기본 목록에 남으면 삭제가 안 된 것처럼 보인다
        status = status == null ? AdminStoreStatusFilter.OPEN : status;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
    
    // 검색어가 없으면 "%"로 넘겨 쿼리에서 null 분기를 없앤다. name·address 모두 NOT NULL
    public String keywordPattern() {
        if (keyword == null || keyword.isBlank()) {
            return MATCH_ALL;
        }
        // 서버 기본 Locale에 따라 대소문자 변환 결과가 달라지지 않게 ROOT 고정. 쿼리도 lower()로 맞춘다.
        return MATCH_ALL + escapeLike(keyword.strip().toLowerCase(Locale.ROOT)) + MATCH_ALL;
    }
    
    // Like에서 %와 _는 와일드카드라 그대로 넘기면 "10%" 검색이 "10" 검색이 된다.
    // 이스케이프 문자인 역슬래시를 먼저 바꿔야 뒤에서 붙인 역슬래시를 다시 바꾸지 않는다.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
