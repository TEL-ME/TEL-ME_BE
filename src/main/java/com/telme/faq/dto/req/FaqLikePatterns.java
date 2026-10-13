package com.telme.faq.dto.req;

import com.telme.faq.entity.FaqCategory;
import java.util.Locale;

final class FaqLikePatterns {

    // 조건을 안 준 항목은 "%"로 넘겨 쿼리에서 null 분기를 없앤다. question·answer·category 모두 NOT NULL
    private static final String MATCH_ALL = "%";

    private FaqLikePatterns() {
    }

    static String keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return MATCH_ALL;
        }
        // 서버 기본 Locale에 따라 대소문자 변환 결과가 달라지지 않게 ROOT 고정. 쿼리도 lower()로 맞춘다
        return MATCH_ALL + escapeLike(keyword.strip().toLowerCase(Locale.ROOT)) + MATCH_ALL;
    }

    // 카테고리는 10종 고정이라 enum으로 받는다. 오타는 여기 닿기 전에 400이 된다.
    // NAME_CHANGE처럼 이름에 _가 들어가 검색어와 같은 이스케이프가 필요하다
    static String category(FaqCategory category) {
        return category == null ? MATCH_ALL : escapeLike(category.name());
    }

    // LIKE에서 %와 _는 와일드카드라 그대로 넘기면 "10%" 검색이 "10" 검색이 된다.
    // 이스케이프 문자인 역슬래시를 먼저 바꿔야 뒤에서 붙인 역슬래시를 다시 바꾸지 않는다
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
