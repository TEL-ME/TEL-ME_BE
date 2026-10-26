package com.telme.store.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

// 카카오모빌리티 자동차 길찾기(/v1/directions) 응답 중 쓰는 필드만 받는다.
// vertexes는 [x1, y1, x2, y2, ...] 1차원 배열이고 x는 경도, y는 위도다
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoNaviDirectionsResponse(List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Route(Integer resultCode, String resultMsg, Summary summary, List<Section> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Integer distance, Integer duration) { // 미터, 초
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(List<Road> roads) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Road(List<Double> vertexes) {
    }
}
