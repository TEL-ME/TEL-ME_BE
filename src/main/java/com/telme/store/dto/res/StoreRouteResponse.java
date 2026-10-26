package com.telme.store.dto.res;

import com.telme.store.dto.req.RouteMode;
import java.util.List;
import lombok.Builder;

// 수단과 관계없이 같은 모양으로 내려 FE가 steps의 path를 이어 Polyline으로 그리기만 하면 된다.
// fare·transfers는 대중교통만 값이 있고 나머지는 null이다.
// 대중교통은 카카오가 추천 순으로 주는 경로 중 첫 경로를 이 응답의 값으로, 나머지를 alternatives에 같은 모양으로 담는다.
// originWalkIncluded는 대중교통에서 출발지부터 첫 정류장까지 걷는 단계를 steps 앞에 붙였는지다(다른 수단은 null).
// 걷는 단계 보강은 도보 API 예산을 쓰므로 첫 경로에만 붙이고, alternatives는 첫 정류장부터 시작한다
@Builder
public record StoreRouteResponse(
        RouteMode mode,
        int distanceMeters,
        int durationSeconds,
        Integer fare,
        Integer transfers,
        List<Step> steps,
        Boolean originWalkIncluded,
        List<StoreRouteResponse> alternatives
) {

    public StoreRouteResponse {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public StoreRouteResponse(RouteMode mode, int distanceMeters, int durationSeconds, Integer fare, Integer transfers,
                              List<Step> steps) {
        this(mode, distanceMeters, durationSeconds, fare, transfers, steps, null, List.of());
    }

    // 출발지와 도착지가 같아 갈 필요가 없을 때의 응답이다. 대중교통은 도보 보강을 하지 않았으므로 originWalkIncluded를 false로 둔다
    public static StoreRouteResponse arrived(RouteMode mode) {
        Boolean originWalkIncluded = mode == RouteMode.TRANSIT ? false : null;
        return new StoreRouteResponse(mode, 0, 0, null, null, List.of(), originWalkIncluded, List.of());
    }

    public StoreRouteResponse withAlternatives(List<StoreRouteResponse> alternatives) {
        return new StoreRouteResponse(mode, distanceMeters, durationSeconds, fare, transfers, steps,
                originWalkIncluded, alternatives);
    }

    public record Step(
            StepType type,
            String guidance,
            int distanceMeters,
            int durationSeconds,
            String lineName, // 버스 번호·지하철 노선 이름. 대중교통의 BUS·SUBWAY 단계만 값이 있다
            List<Coordinate> path
    ) {
    }

    public record Coordinate(double latitude, double longitude) {
    }

    public enum StepType {
        DRIVE,
        WALK,
        BICYCLE,
        BUS,
        SUBWAY
    }
}
