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
        requireObject(transit, "transit");
        requireObject(walk, "walk");
        requireObject(transit.steps(), "transit.steps");
        requireObject(walk.steps(), "walk.steps");
        List<Step> steps = new ArrayList<>(walk.steps());
        steps.addAll(transit.steps());
        return StoreRouteResponse.builder()
                .mode(RouteMode.TRANSIT)
                .distanceMeters(addMetrics(transit.distanceMeters(), walk.distanceMeters(), "totalDistance"))
                .durationSeconds(addMetrics(transit.durationSeconds(), walk.durationSeconds(), "totalTime"))
                .fare(transit.fare())
                .transfers(transit.transfers())
                .steps(List.copyOf(steps))
                .build();
    }

    // 자동차는 안내 단위 없이 도로별 좌표를 이어 한 구간으로 내려 준다
    public StoreRouteResponse toCarResponse(KakaoNaviDirectionsResponse.Route route) {
        requireObject(route, "route");
        requireObject(route.summary(), "summary");
        int distance = requireMetric(route.summary().distance(), "summary.distance");
        int duration = requireMetric(route.summary().duration(), "summary.duration");
        List<Coordinate> path = new ArrayList<>();
        for (var section : requireElements(route.sections(), "sections")) {
            for (var road : requireElements(section.roads(), "sections.roads")) {
                if (road.vertexes() != null) {
                    appendVertexes(path, road.vertexes());
                }
            }
        }
        if ((distance > 0 || duration > 0) && path.isEmpty()) {
            throw invalidResponse("sections.roads.vertexes");
        }
        Step step = new Step(StepType.DRIVE, null, distance, duration, null, path);
        return new StoreRouteResponse(RouteMode.CAR, distance, duration, null, null, List.of(step));
    }

    public StoreRouteResponse toTransitResponse(KakaoTransitRouteResponse.Route route) {
        KakaoTransitRouteResponse.RouteProperties properties =
                requireObject(requireObject(route, "route").properties(), "properties");
        int distance = requireMetric(properties.totalDistance(), "properties.totalDistance");
        int duration = requireMetric(properties.totalTime(), "properties.totalTime");
        List<Step> steps = requireElements(route.steps(), "steps").stream()
                .map(this::toTransitStep)
                .toList();
        if ((distance > 0 || duration > 0) && steps.isEmpty()) {
            throw invalidResponse("steps");
        }
        Integer fare = properties.fare() == null ? null
                : requireNonNegative(properties.fare().value(), "fare.value");
        Integer transfers = requireNonNegative(properties.transfers(), "transfers");
        return new StoreRouteResponse(RouteMode.TRANSIT, distance, duration, fare, transfers, steps);
    }

    // 도보·자전거 응답은 legs[].steps[] 구조다. 경유지를 쓰지 않으므로 leg는 하나지만 여러 개여도 순서대로 이어 붙인다
    public StoreRouteResponse toLegResponse(RouteMode mode, KakaoLegRouteResponse.Route route) {
        requireObject(requireObject(route, "route").properties(), "properties");
        int distance = requireMetric(route.properties().totalDistance(), "properties.totalDistance");
        int duration = requireMetric(route.properties().totalTime(), "properties.totalTime");
        StepType type = mode == RouteMode.BICYCLE ? StepType.BICYCLE : StepType.WALK;
        List<Step> steps = requireElements(route.legs(), "legs").stream()
                .flatMap(leg -> requireElements(leg.steps(), "legs.steps").stream())
                .map(step -> toLegStep(type, step))
                .toList();
        if ((distance > 0 || duration > 0) && steps.isEmpty()) {
            throw invalidResponse("legs.steps");
        }
        return new StoreRouteResponse(mode, distance, duration, null, null, steps);
    }

    private Step toTransitStep(KakaoTransitRouteResponse.Step step) {
        KakaoTransitRouteResponse.StepProperties properties =
                requireObject(requireObject(step, "step").properties(), "steps.properties");
        return new Step(toTransitStepType(properties.type()), properties.guidance(),
                requireMetric(properties.distance(), "steps.properties.distance"),
                requireMetric(properties.time(), "steps.properties.time"), findLineName(properties),
                toCoordinates(step.path() == null ? null : step.path().points()));
    }

    private Step toLegStep(StepType type, KakaoLegRouteResponse.Step step) {
        KakaoLegRouteResponse.StepProperties properties =
                requireObject(requireObject(step, "step").properties(), "steps.properties");
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
        List<Coordinate> coordinates = new ArrayList<>();
        for (List<Double> point : elementsOrEmpty(points, "path.points")) {
            if (point.size() < 2) {
                throw invalidResponse("path.points: 좌표 부족");
            }
            coordinates.add(coordinate(point.get(0), point.get(1)));
        }
        return List.copyOf(coordinates);
    }

    private void appendVertexes(List<Coordinate> path, List<Double> vertexes) {
        if (vertexes.size() % 2 != 0) {
            throw invalidResponse("vertexes: 좌표 부족");
        }
        for (int i = 0; i < vertexes.size(); i += 2) {
            path.add(coordinate(vertexes.get(i), vertexes.get(i + 1)));
        }
    }

    private Coordinate coordinate(Double longitude, Double latitude) {
        if (longitude == null || latitude == null || !Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw invalidResponse("path: 잘못된 좌표");
        }
        return new Coordinate(latitude, longitude);
    }

    private <T> List<T> requireElements(List<T> values, String field) {
        if (values == null || values.isEmpty()) {
            throw invalidResponse(field);
        }
        for (T value : values) {
            requireObject(value, field);
        }
        return values;
    }

    private <T> List<T> elementsOrEmpty(List<T> values, String field) {
        if (values == null) {
            return List.of();
        }
        for (T value : values) {
            requireObject(value, field);
        }
        return values;
    }

    private Integer requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            log.warn("[KakaoRoute] 음수 필드 field={}", field);
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
        return value;
    }

    private <T> T requireObject(T value, String field) {
        if (value == null) {
            throw invalidResponse(field);
        }
        return value;
    }

    private int addMetrics(int first, int second, String field) {
        long total = (long) first + second;
        if (first < 0 || second < 0 || total > Integer.MAX_VALUE) {
            throw invalidResponse(field);
        }
        return (int) total;
    }

    private GeneralException invalidResponse(String field) {
        log.warn("[KakaoRoute] 잘못된 응답 구조·값 field={}", field);
        return new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
    }

    // 누락된 측정값은 도착(0m·0초)과 다르므로 정상 응답으로 위장하지 않는다.
    private int requireMetric(Integer value, String field) {
        if (value == null || value < 0) {
            log.warn("[KakaoRoute] 거리·시간 필드 누락 또는 음수 field={}", field);
            throw new GeneralException(StoreErrorCode.ROUTE_UNAVAILABLE);
        }
        return value;
    }
}
