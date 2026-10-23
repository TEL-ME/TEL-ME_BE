package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.config.StoreOriginWalkProperties;
import com.telme.store.dto.req.StoreOriginWalkRequest;
import com.telme.store.exception.StoreErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 출발 도보에만 적용한다. 경로 데이터는 보관하지 않고 IP별 요청 수만 서버 메모리에 둔다.
// 여러 서버에서는 서버마다 한도가 적용되며, 공용 일일 WALK 예산은 DB에서 별도로 제한한다.
@Component
@RequiredArgsConstructor
public class StoreOriginWalkGuard {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_CLIENTS = 10_000;
    private final StoreOriginWalkProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public void checkDistance(StoreOriginWalkRequest request) {
        double originLatitude = Math.toRadians(request.originLatitude());
        double destinationLatitude = Math.toRadians(request.destinationLatitude());
        double latitudeDelta = destinationLatitude - originLatitude;
        double longitudeDelta = Math.toRadians(request.destinationLongitude() - request.originLongitude());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatitude) * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double distance = 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
        if (!Double.isFinite(distance) || distance > properties.maxStraightDistanceMeters()) {
            throw new GeneralException(StoreErrorCode.ROUTE_DISTANCE_EXCEEDED);
        }
    }

    // 첫 허용 요청부터 60초 동안 센다. 외부 호출이 실패해도 시도 횟수는 돌려주지 않는다.
    public synchronized void acquire(String clientIp) {
        Instant now = clock.instant();
        Window window = windows.get(clientIp);
        if (window != null && !now.isBefore(window.startedAt().plus(WINDOW))) {
            windows.remove(clientIp);
            window = null;
        }
        if (window == null && windows.size() >= MAX_CLIENTS) {
            windows.values().removeIf(value -> !now.isBefore(value.startedAt().plus(WINDOW)));
            // 임의 IP로 메모리를 무한히 늘리지 않는다. 활성 창을 삭제해 제한을 우회시키지도 않는다.
            if (windows.size() >= MAX_CLIENTS) {
                throw new GeneralException(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED);
            }
        }
        if (window == null) {
            window = new Window(now, 0);
        }
        if (window.count() >= properties.requestsPerMinute()) {
            throw new GeneralException(StoreErrorCode.ORIGIN_WALK_RATE_LIMIT_EXCEEDED);
        }
        windows.put(clientIp, new Window(window.startedAt(), window.count() + 1));
    }

    private record Window(Instant startedAt, int count) {
    }
}
