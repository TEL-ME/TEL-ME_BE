package com.telme.store.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 카카오맵 대중교통 경로(/v2/routing/publictraffic) 응답 중 쓰는 필드만 받는다.
// 결과는 routes가 아니라 status로 구분하고, 좌표 points는 [x, y] 쌍(x 경도, y 위도)이다
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTransitRouteResponse(String status, List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(RouteProperties properties, List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteProperties(Integer totalDistance, Integer totalTime, Integer transfers, Fare fare) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fare(Integer value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(StepProperties properties, Path path) {
    }

    // stops: 그 단계의 정류장·역. 걷는 단계는 [출발한 곳, 도착할 곳] 두 개다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepProperties(String guidance, String type, Integer distance, Integer time,
                                 List<Vehicle> vehicles, List<Stop> stops) { // type: BUS, SUBWAY, WALKING

        public StepProperties(String guidance, String type, Integer distance, Integer time, List<Vehicle> vehicles) {
            this(guidance, type, distance, time, vehicles, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stop(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Path(List<List<Double>> points) {
    }
}
