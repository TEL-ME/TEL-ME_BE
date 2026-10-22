package com.telme.store.converter;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.req.RouteMode;
import com.telme.store.dto.res.KakaoLegRouteResponse;
import com.telme.store.dto.res.KakaoNaviDirectionsResponse;
import com.telme.store.dto.res.KakaoTransitRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.dto.res.StoreRouteResponse.Coordinate;
import com.telme.store.dto.res.StoreRouteResponse.Step;
import com.telme.store.dto.res.StoreRouteResponse.StepType;
import com.telme.store.exception.StoreErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 카카오 경로 응답을 수단과 관계없는 StoreRouteResponse로 바꾼다. 카카오 좌표는 [경도, 위도], 응답은 위도·경도 이름이 붙은 값이다
@Component
@Slf4j
public class StoreRouteConverter {

    private static final String TRANSIT_STEP_BUS = "BUS";
    private static final String TRANSIT_STEP_SUBWAY = "SUBWAY";

    public StoreRouteResponse prependOriginWalk(StoreRouteResponse transit, StoreRouteResponse walk) {
        List<Step> steps = new ArrayList<>(walk.steps());
        steps.addAll(transit.steps());
        return StoreRouteResponse.builder()
                .mode(RouteMode.TRANSIT)
                .distanceMeters(transit.distanceMeters() + walk.distanceMeters())
                .durationSeconds(transit.durationSeconds() + walk.durationSeconds())
                .fare(transit.fare())
                .transfers(transit.transfers())
                .steps(List.copyOf(steps))
                .build();
    }

    // 자동차는 안내 단위 없이 도로별 좌표를 이어 한 구간으로 내려 준다
    public StoreRouteResponse toCarResponse(KakaoNaviDirectionsResponse.Route route) {
        List<Coordinate> path = new ArrayList<>();
        if (route.sections() != null) {
            route.sections().stream()
                    .filter(section -> section.roads() != null)
                    .flatMap(section -> section.roads().stream())
                    .filter(road -> road.vertexes() != null)
                    .forEach(road -> appendVertexes(path, road.vertexes()));
        }
        int distance = requireMetric(route.summary().distance(), "summary.distance");
        int duration = requireMetric(route.summary().duration(), "summary.duration");
        Step step = new Step(StepType.DRIVE, null, distance, duration, null, path);
        return new StoreRouteResponse(RouteMode.CAR, distance, duration, null, null, List.of(step));
    }

    public StoreRouteResponse toTransitResponse(KakaoTransitRouteResponse.Route route) {
        KakaoTransitRouteResponse.RouteProperties properties = route.properties();
        List<Step> steps = route.steps() == null ? List.of() : route.steps().stream()
                .map(this::toTransitStep)
                .toList();
        Integer fare = properties.fare() == null ? null : properties.fare().value();
        return new StoreRouteResponse(RouteMode.TRANSIT,
                requireMetric(properties.totalDistance(), "properties.totalDistance"),
                requireMetric(properties.totalTime(), "properties.totalTime"), fare, properties.transfers(), steps);
    }

    // 도보·자전거 응답은 legs[].steps[] 구조다. 경유지를 쓰지 않으므로 leg는 하나지만 여러 개여도 순서대로 이어 붙인다
    public StoreRouteResponse toLegResponse(RouteMode mode, KakaoLegRouteResponse.Route route) {
        StepType type = mode == RouteMode.BICYCLE ? StepType.BICYCLE : StepType.WALK;
        List<Step> steps = route.legs() == null ? List.of() : route.legs().stream()
                .filter(leg -> leg.steps() != null)
                .flatMap(leg -> leg.steps().stream())
                .map(step -> toLegStep(type, step))
                .toList();
        return new StoreRouteResponse(mode,
                requireMetric(route.properties().totalDistance(), "properties.totalDistance"),
                requireMetric(route.properties().totalTime(), "properties.totalTime"), null, null, steps);
    }

    private Step toTransitStep(KakaoTransitRouteResponse.Step step) {
        KakaoTransitRouteResponse.StepProperties properties = step.properties();
        return new Step(toTransitStepType(properties.type()), properties.guidance(),
                requireMetric(properties.distance(), "steps.properties.distance"),
                requireMetric(properties.time(), "steps.properties.time"), findLineName(properties),
                toCoordinates(step.path() == null ? null : step.path().points()));
    }

    private Step toLegStep(StepType type, KakaoLegRouteResponse.Step step) {
        KakaoLegRouteResponse.StepProperties properties = step.properties();
        return new Step(type, properties.guidance(),
                requireMetric(properties.distance(), "steps.properties.distance"),
                requireMetric(properties.time(), "steps.properties.time"),
                null, toCoordinates(step.path() == null ? null : step.path().points()));
    }

    // 문서에 없는 단계 종류가 와도 지도에는 걸어가는 구간으로 그려 경로 전체를 버리지 않는다
    private StepType toTransitStepType(String type) {
        if (TRANSIT_STEP_BUS.equals(type)) {
            return StepType.BUS;
        }
        if (TRANSIT_STEP_SUBWAY.equals(type)) {
            return StepType.SUBWAY;
        }
        return StepType.WALK;
    }

    private String findLineName(KakaoTransitRouteResponse.StepProperties properties) {
        if (!TRANSIT_STEP_BUS.equals(properties.type()) && !TRANSIT_STEP_SUBWAY.equals(properties.type())) {
            return null;
        }
        List<KakaoTransitRouteResponse.Vehicle> vehicles = properties.vehicles();
        // 탑승 단계인데 노선명이 없으면 사용할 수 없는 안내를 정상 응답으로 내리지 않는다.
        if (vehicles == null || vehicles.isEmpty() || vehicles.get(0) == null
                || vehicles.get(0).name() == null || vehicles.get(0).name().isBlank()) {
            log.warn("[KakaoTransit] 탑승 단계 노선명 누락 type={}", properties.type());
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
        return vehicles.get(0).name();
    }

    private List<Coordinate> toCoordinates(List<List<Double>> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .filter(point -> point != null && point.size() >= 2)
                .map(point -> new Coordinate(point.get(1), point.get(0)))
                .toList();
    }

    // [x1, y1, x2, y2, ...] 쌍을 맞춰 읽고, 짝이 안 맞는 마지막 값은 버린다
    private void appendVertexes(List<Coordinate> path, List<Double> vertexes) {
        for (int i = 0; i + 1 < vertexes.size(); i += 2) {
            path.add(new Coordinate(vertexes.get(i + 1), vertexes.get(i)));
        }
    }

    // 누락된 측정값은 도착(0m·0초)과 다르므로 정상 응답으로 위장하지 않는다.
    private int requireMetric(Integer value, String field) {
        if (value == null) {
            log.warn("[KakaoRoute] 거리·시간 필드 누락 field={}", field);
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
        return value;
    }
}
