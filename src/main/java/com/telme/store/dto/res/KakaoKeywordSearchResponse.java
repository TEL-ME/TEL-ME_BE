package com.telme.store.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

// 카카오 로컬 키워드 검색(/v2/local/search/keyword.json) 응답 중 쓰는 필드만 받는다. x는 경도, y는 위도다
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Document(String placeName, String x, String y) {
    }
}
