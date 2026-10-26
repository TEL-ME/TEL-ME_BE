package com.telme.store.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

// 카카오 로컬 주소 검색(/v2/local/search/address.json) 응답 중 쓰는 필드만 받는다. x는 경도, y는 위도다
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressSearchResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Document(
            String addressName,
            String addressType, // REGION(지명), ROAD(도로명), REGION_ADDR(지번 주소), ROAD_ADDR(도로명 주소)
            String x,
            String y,
            Address address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Address(String bCode) { // 법정동코드 10자리
    }
}
