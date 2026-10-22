package com.telme.store.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 카카오맵 도보(/v2/routing/walk)·자전거(/v2/routing/bicycle) 경로 응답 중 쓰는 필드만 받는다. 두 응답의 모양이 같다.
// 결과는 route가 아니라 status로 구분하고, 좌표 points는 [x, y] 쌍(x 경도, y 위도)이다
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLegRouteResponse(String status, Route route) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(RouteProperties properties, List<Leg> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteProperties(Integer totalDistance, Integer totalTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(StepProperties properties, Path path) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepProperties(String guidance, Integer distance, Integer time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(List<List<Double>> points) {
    }
}
