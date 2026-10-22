package com.telme.store.dto.res;

import com.telme.store.dto.req.RouteMode;
import java.util.List;
import lombok.Builder;

// 수단과 관계없이 같은 모양으로 내려 FE가 steps의 path를 이어 Polyline으로 그리기만 하면 된다.
// fare·transfers는 대중교통만 값이 있고 나머지는 null이다
@Builder
public record StoreRouteResponse(
        RouteMode mode,
        int distanceMeters,
        int durationSeconds,
        Integer fare,
        Integer transfers,
        List<Step> steps
) {

    // 출발지와 도착지가 같아 갈 필요가 없을 때의 응답이다
    public static StoreRouteResponse arrived(RouteMode mode) {
        return new StoreRouteResponse(mode, 0, 0, null, null, List.of());
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
